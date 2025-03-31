package com.example.connectdevice.ui.nfc;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.NfcV;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;

import java.io.IOException;
import java.util.Arrays;

public class NfcViewModel extends AndroidViewModel {

    private NfcAdapter nfcAdapter;

    public NfcViewModel(Application application) {
        super(application);
        nfcAdapter = NfcAdapter.getDefaultAdapter(application);
    }

    public PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, context.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE);
    }

    public IntentFilter[] getIntentFilters() {
        return new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
    }

    public void writeToNfcVTag(NfcV nfcv, byte blockAddress, String data) {
        try {

            if (!nfcv.isConnected()) {
                nfcv.connect();
                Thread.sleep(100);  // Delay para estabilizar a conexão
            }

            // UID da tag
            byte[] uid = nfcv.getTag().getId();
           // byte[] dataToWrite = new byte[]{data, 0x00, 0x00, 0x00};
            byte[] dataToWrite = data.getBytes();

            //byte[] writeCommand = createWriteCommand(uid, blockAddress, dataToWrite);
            byte[] writeCommand = writeMultipleBlocks(uid, blockAddress, dataToWrite);

            // Executa a escrita
            byte[] response = nfcv.transceive(writeCommand);
            if (response[0] == 0) {
                Log.d("NFC", "Escrita realizada com sucesso no bloco " + blockAddress + ": " + data);
            } else {
                Log.d("NFC", "Erro ao realizar escrita : " + bytesToHex(response));
            }


            nfcv.close();

        } catch (IOException | InterruptedException e) {
            Log.e("NFC_WRITE", "Erro ao escrever na tag: " + e.getMessage());
        }
    }

    private byte[] createWriteCommand(byte[] uid, byte blockAddress, byte[] dataToWrite) {

        try {

            return new byte[]{
                    (byte) 0x22,  // Flag de endereçamento
                    (byte) 0x21,  // Comando de escrita de bloco único
                    uid[0], uid[1], uid[2], uid[3], uid[4], uid[5], uid[6], uid[7],  // UID completo
                    blockAddress, // Endereço do bloco
                    dataToWrite[0], dataToWrite[1], dataToWrite[2], dataToWrite[3]  // Dados a escrever (4 bytes)
            };

        } catch (Exception e) {
            Log.e("NFC_WRITE", "Erro ao criar comando: " + e.getMessage());
        }

        return null;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        byte[] realData = Arrays.copyOfRange(bytes, 1, bytes.length);

        for (byte b : realData) {
            sb.append(String.format("%02X ", b));
        }

        return sb.toString().trim();
    }

    public String readNfcVTag(NfcV nfcv, byte blockAddress) {

        try {

            nfcv.close();
            ;
            nfcv.connect();
            byte[] readCommand = new byte[]{0x02, 0x20, blockAddress};
            byte[] responseRead = nfcv.transceive(readCommand);
            nfcv.close();
            Log.d("NFC", "readCommand response: " + bytesToHex(responseRead));

            return bytesToHex(responseRead);

        } catch (Exception e) {
            Log.e("NFC", "readCommand Exception: " + e.getMessage());
        }

        return null;
    }

    private byte[] writeMultipleBlocks(byte[] uid, byte blockAddress, byte[] data) {
        try {
            int numBlocks = (int) Math.ceil(data.length / 4.0);

            // Ajusta os dados para múltiplos de 4 bytes, preenchendo com 0x00
            byte[] paddedData = new byte[numBlocks * 4];
            System.arraycopy(data, 0, paddedData, 0, data.length);

            // Comando de escrita em múltiplos blocos (0x24)
            byte[] writeCommand = new byte[12 + paddedData.length];
            writeCommand[0] = 0x22;  // Flag (endereçamento usando UID)
            writeCommand[1] = 0x24;  // Comando de múltiplos blocos
            System.arraycopy(uid, 0, writeCommand, 2, 8);  // UID (8 bytes)
            writeCommand[10] = blockAddress; // Bloco inicial
            writeCommand[11] = (byte) (numBlocks - 1); // Número de blocos - 1

            // Copia os dados ajustados para o comando
            System.arraycopy(paddedData, 0, writeCommand, 12, paddedData.length);

            return writeCommand;

        } catch (Exception e) {
            Log.e("NFC_WRITE", "Erro ao escrever múltiplos blocos: " + e.getMessage());
        }
        return null;
    }

    public void clear(NfcV nfcv){
        try {

            byte[] dataToWrite = new byte[]{0x00, 0x00, 0x00, 0x00};
            int v = 0;
            for (int i = 0; i <= 63; i ++) {
                nfcv.close();
                nfcv.connect();
                byte[] uid = nfcv.getTag().getId();
                byte blockAddress = (byte) i;
                byte[] writeCommand =  createWriteCommand(uid, blockAddress ,dataToWrite);

                byte[] response = nfcv.transceive(writeCommand);

                if (response[0] == 0) {
                    Log.d("NFC", "Escrita realizada com sucesso ");
                } else {
                    Log.d("NFC", "Erro ao realizar escrita : " + bytesToHex(response));
                }
                v++;
            }

            nfcv.close();
        }catch (Exception e){
            Log.e("NFC", "Escrita com erro");
        }
    }
}