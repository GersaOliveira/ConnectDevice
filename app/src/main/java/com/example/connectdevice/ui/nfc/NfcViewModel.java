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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            // Delay para estabilizar a conexão
            nfcv.close();
            Thread.sleep(5);
            nfcv.connect();
            Thread.sleep(5);

            // UID da tag
            byte[] uid = nfcv.getTag().getId();
            byte[] dataToWrite = data.getBytes();

            // Pad the data to 256 bytes
            byte[] paddedData;

            if (dataToWrite.length > 256) {
                Log.e("NFC_WRITE", "Quantidade de bytes superior a 256");
                return;
            }
             paddedData = padDataTo256Bytes(dataToWrite);

            int maxByte = 16;
            int numBlocks = paddedData.length / maxByte;

            for (int i = 0; i < numBlocks; i++) {
                int startIndex = i * maxByte;
                byte[] blockData = Arrays.copyOfRange(paddedData, startIndex, startIndex + maxByte);

                byte[] writeCommand = writeMultipleBlocks(uid, (byte)(blockAddress), blockData);

                // Executa a escrita
                byte[] response = nfcv.transceive(writeCommand);
                if (response[0] == 0) {
                    blockAddress += 4;
                    Log.d("NFC", "Escrita realizada com sucesso no bloco " + (blockAddress + i) + ": " + bytesToHex(blockData));
                } else {
                    Log.d("NFC", "Erro ao realizar escrita no bloco " + (blockAddress + i) + ": " + bytesToHex(response));
                    // Você pode decidir se quer parar aqui ou continuar tentando os próximos blocos
                }

                // Pequena pausa entre as escritas
                Thread.sleep(25);
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
            Thread.sleep(100);
            nfcv.close();
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
            // Comando de escrita em múltiplos blocos (0x24)
            byte[] writeCommand = new byte[12 + data.length];
            writeCommand[0] = 0x22;  // Flag (endereçamento usando UID)
            writeCommand[1] = 0x24;  // Comando de múltiplos blocos

            System.arraycopy(uid, 0, writeCommand, 2, 8);  // UID (8 bytes)

            writeCommand[10] = blockAddress; // Bloco inicial
            writeCommand[11] = (byte) ((data.length / 4) - 1); // Número de blocos - 1

            // Copia os dados para o comando
            System.arraycopy(data, 0, writeCommand, 12, data.length);

            return writeCommand;

        } catch (Exception e) {
            Log.e("NFC_WRITE", "Erro ao criar comando de escrita múltipla: " + e.getMessage());
        }
        return null;
    }

    public void clear(NfcV nfcv){
        try {
            byte[] dataToWrite = new byte[]{0x00, 0x00, 0x00, 0x00};


            byte[] uid = nfcv.getTag().getId();

            for (int i = 0; i < 63; i ++) {

                nfcv.connect();
                Thread.sleep(50);
                byte blockAddress = (byte) i;
                byte[] writeCommand =  createWriteCommand(uid, blockAddress ,dataToWrite);

                byte[] response = nfcv.transceive(writeCommand);
                nfcv.close();
                Thread.sleep(50);
                if (response[0] == 0) {
                    Log.d("NFC", "Escrita realizada com sucesso ");
                } else {
                    Log.d("NFC", "Erro ao realizar escrita : " + bytesToHex(response));
                }
            }

        }catch (Exception e){
            Log.e("NFC", "Escrita com erro: " + e.toString());
        }
    }

    private byte[] padDataTo256Bytes(byte[] data) {
        byte[] paddedData = new byte[256];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        // The rest of the array is automatically filled with zeros
        return paddedData;
    }

    public ArrayList<String> readMultipleBlocks(NfcV nfcv, byte firstBlockNumber, int numberOfBlocks) {
        if (numberOfBlocks < 1 || numberOfBlocks > 256) {
            throw new IllegalArgumentException("Number of blocks must be between 1 and 256");
        }
        ArrayList<String> addressList = new ArrayList<String>();
        try {
            byte[] uid = nfcv.getTag().getId();

            // Construir o comando de leitura múltipla
            byte[] readCommand = new byte[]{
                    (byte) 0x22,  // Flags (usando endereçamento)
                    (byte) 0x23,  // Comando Read Multiple Block
                    uid[0], uid[1], uid[2], uid[3], uid[4], uid[5], uid[6], uid[7],  // UID (8 bytes)
                    firstBlockNumber,  // Número do primeiro bloco
                    (byte)(numberOfBlocks - 1)  // Número de blocos - 1
            };

            Log.d("NFC_READ", "Sending read command: " + bytesToHex(readCommand));

            // Enviar o comando e receber a resposta
            byte[] response = nfcv.transceive(readCommand);

            Log.d("NFC_READ", "Received response: " + bytesToHex(response));

            // Verificar se a resposta é válida
            if (response[0] == 0x00) {
                // A resposta bem-sucedida começa com 0x00, seguida pelos dados
                byte[] data = Arrays.copyOfRange(response, 1, response.length);
                Log.d("NFC_READ", "Read successful. Data: " + bytesToHex(data));
                if (data != null) {

                    for (int i = 0; i < data.length; i += 4) {
                        byte[] block = Arrays.copyOfRange(data, i, i + 4);
                        String valor = bytesToHexR(block);
                        addressList.add("Address " + i + ": " + valor);
                    }
                }
                return addressList;
            } else {
                Log.e("NFC_READ", "Read failed. Error code: " + String.format("%02X", response[0]));
                return null;
            }
        } catch (IOException e) {
            Log.e("NFC_READ", "Error reading from tag: " + e.getMessage());
            return null;
        }
    }

    private String bytesToHexR(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}