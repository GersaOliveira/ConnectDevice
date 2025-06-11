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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class NfcViewModel extends AndroidViewModel {

    private NfcAdapter nfcAdapter;
    private ArrayList<byte[]> listValue;

    public boolean StatusRead;


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

    public void writeMultipleBlocks(NfcV nfcv, byte blockAddress, String data, boolean bSwitch) {
        try {

            // UID da tag
            byte[] uid = nfcv.getTag().getId();
            String[] byteStrings = data.split(",");

            int count = Math.min(byteStrings.length, 256);

            byte[] numericBytes = new byte[byteStrings.length];

            for (int i = 0; i < count; i++) {

                String trimmedByteString = byteStrings[i].trim(); // Remove espaços em branco
                if (trimmedByteString.isEmpty()) {
                    Log.e("NFC_WRITE", "Valor de byte vazio encontrado na string de dados: " + data);
                    // Tratar o erro adequadamente, talvez retornando ou lançando uma exceção
                }
                // Converte a string para byte.
                // Byte.parseByte() espera valores entre -128 e 127.

                numericBytes[i] = Byte.parseByte(trimmedByteString);

            }


            byte[] dataToWrite = numericBytes;

            // Pad the data to 256 bytes
            byte[] paddedData;

            if (dataToWrite.length > 256) {
                Log.e("NFC_WRITE", "Quantidade de bytes superior a 256");
            }
            paddedData = padDataTo256Bytes(dataToWrite);

            int maxByte = 16;
            int numBlocks = paddedData.length / maxByte;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            if (bSwitch) {
                // Se tst for true, a intenção é usar listValue como fonte,
                // processar seus blocos e o resultado se torna o novo paddedData.
                if (listValue != null && !listValue.isEmpty()) {
                    for (byte[] block : listValue) {
                        if (block != null) {
                            try {
                                byte[] incrementedBlock = block;
                                // byte[] incrementedBlock = incrementLittleEndian(block);
                                if (incrementedBlock != null) {
                                    outputStream.write(incrementedBlock);
                                } else {
                                    Log.w("NFC_CONVERT", "incrementLittleEndian retornou null para o bloco: " + (block != null ? bytesToHex(block) : "null"));
                                }
                            } catch (IOException e) {
                                Log.e("NFC_CONVERT", "Erro ao escrever bloco incrementado para ByteArrayOutputStream", e);
                            }
                        } else {
                            Log.w("NFC_CONVERT", "Bloco nulo encontrado em listValue.");
                        }
                    }
                    paddedData = outputStream.toByteArray();
                    Log.d("NFC_TST_MODE", "paddedData atualizado a partir de listValue (tst=true): " + (paddedData != null ? bytesToHex(paddedData) : "null"));

                    if (paddedData.length != 256) {
                        Log.w("NFC_TST_MODE", "paddedData (de listValue) tem " + paddedData.length + " bytes. Ajustando para 256 bytes.");
                        byte[] tempPaddedData = new byte[256];
                        System.arraycopy(paddedData, 0, tempPaddedData, 0, Math.min(paddedData.length, 256));
                        paddedData = tempPaddedData;
                    }

                } else {
                    Log.w("NFC_TST_MODE", "listValue está nula ou vazia, mas tst=true. paddedData não será modificado a partir de listValue.");
                }
            }

            if (!nfcv.isConnected()) {
                nfcv.close();
                Thread.sleep(5);
                nfcv.connect();
            }

            manageGPO(uid, (byte) 0x00, nfcv);


            for (int i = 0; i < numBlocks; i++) {

                int startIndex = i * maxByte;
                byte[] blockData = Arrays.copyOfRange(paddedData, startIndex, startIndex + maxByte);

                byte[] writeCommand = CommandWriteMultipleBlocks(uid, (byte) (blockAddress), blockData);

                byte[] response = nfcv.transceive(writeCommand);
                if (response[0] == 0) {
                    blockAddress += 4;
                    Log.d("NFC", "Escrita realizada com sucesso no bloco " + (blockAddress + i) + ": " + bytesToHex(blockData));
                } else {
                    Log.d("NFC", "Erro ao realizar escrita no bloco " + (blockAddress + i) + ": " + bytesToHex(response));
                }

                Thread.sleep(50);

            }

            manageGPO(uid, (byte) 0x01, nfcv);
            nfcv.close();
            Log.d("NFC", "Escrita NFC concluída e processada pelo MCU");

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

    private byte[] CommandWriteMultipleBlocks(byte[] uid, byte blockAddress, byte[] data) {
        try {
            // Comando de escrita em múltiplos blocos (0x24)
            byte[] writeCommand = new byte[12 + data.length];
            writeCommand[0] = 0x22;  // Flag (endereçamento usando UID)
            writeCommand[1] = 0x24;  // Comando de múltiplos blocos

            System.arraycopy(uid, 0, writeCommand, 2, 8); // UID (8 bytes)

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

    public void clear(NfcV nfcv) {
        int i = 0;

        try {

            if (!nfcv.isConnected()) {
                nfcv.close();
                nfcv.connect();
            }


            byte[] uid = nfcv.getTag().getId();
            for (i = 0; i < 64; i++) {

                byte[] dataToWrite = new byte[]{0x00, 0x00, 0x00, 0x00};
                byte blockAddress = (byte) i;
                byte[] writeCommand = createWriteCommand(uid, blockAddress, dataToWrite);

                byte[] response = nfcv.transceive(writeCommand);
                if (response[0] == 0) {
                    Log.d("NFC", "Escrita realizada com sucesso ");
                } else {
                    Log.d("NFC", "Erro ao realizar escrita : " + bytesToHex(response));
                }


                Thread.sleep(50);
            }

            Log.d("NFC", "Erro ao realizar escrita : FIM");
            // nfcv.close();
        } catch (Exception e) {
            Log.e("NFC", "Escrita com erro: " + e.toString());
        }


    }

    private byte[] padDataTo256Bytes(byte[] data) {
        byte[] paddedData = new byte[256];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        return paddedData;
    }

    public ArrayList<String> readMultipleBlocks(NfcV nfcv, byte firstBlockNumber, int numberOfBlocks) throws InterruptedException {
        if (numberOfBlocks < 1 || numberOfBlocks > 256) {
            throw new IllegalArgumentException("Number of blocks must be between 1 and 256");
        }
        ArrayList<String> addressList = new ArrayList<String>();

        listValue = new ArrayList<byte[]>();

        try {
            byte[] uid = nfcv.getTag().getId();

            // Construir o comando de leitura múltipla
            byte[] readCommand = new byte[]{
                    (byte) 0x22,  // Flags (usando endereçamento)
                    (byte) 0x23,  // Comando Read Multiple Block
                    uid[0], uid[1], uid[2], uid[3], uid[4], uid[5], uid[6], uid[7],  // UID (8 bytes)
                    firstBlockNumber,  // Número do primeiro bloco
                    (byte) (numberOfBlocks - 1)  // Número de blocos - 1
            };
            Thread.sleep(50);
            if (!nfcv.isConnected()) {
                nfcv.close();
                nfcv.connect();
            }
            Thread.sleep(50);
            Log.d("NFC_READ", "Sending read command: " + bytesToHex(readCommand));

            // Enviar o comando e receber a resposta
            byte[] response = nfcv.transceive(readCommand);
            Thread.sleep(25);
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
                        listValue.add(block);
                    }
                }

                return addressList;
            } else {
                Log.e("NFC_READ", "Read failed. Error code: " + String.format("%02X", response[0]));
                return null;
            }
        } catch (IOException | InterruptedException e) {
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

    public void manageGPO(byte[] uid, byte gpoValue, NfcV nfcv) {
        try {

            byte[] con = new byte[]{
                    (byte) 0x22,
                    (byte) 0xA9,
                    (byte) 0x02,
                    uid[0], uid[1], uid[2], uid[3], uid[4], uid[5], uid[6], uid[7],  // UID completo
                    (byte) gpoValue};

            byte[] ret = nfcv.transceive(con);


            if (ret[0] == 1 && ret[1] == 2) {
                Log.d("NFC", "Erro manageGPO: comando invalido");
            } else if (ret[0] == 0) {
                Log.d("NFC", "manageGPO OK");
            }

        } catch (Exception e) {
            Log.e("NFC_MANAGE_GPO", "Erro manageGPO: " + e.getMessage());
        }
    }

    public void writeSingleBloc(NfcV nfcv, byte blockAddress, String data) {
        try {

            if (!nfcv.isConnected()) {
                nfcv.close();
                Thread.sleep(50);
                nfcv.connect();
                Thread.sleep(50);
            }

            byte[] paddedData;
            byte[] uid = nfcv.getTag().getId();
            String[] byteStrings = data.split(",");
            int count = Math.min(byteStrings.length, 4);
            byte[] numericBytes = new byte[byteStrings.length];

            for (int i = 0; i < count; i++) {
                String trimmedByteString = byteStrings[i].trim(); // Remove espaços em branco
                if (trimmedByteString.isEmpty()) {
                    Log.e("NFC_WRITE", "Valor de byte vazio encontrado na string de dados: " + data);
                    // Tratar o erro adequadamente, talvez retornando ou lançando uma exceção
                    return; // Exemplo
                }
                // Converte a string para byte.
                // Byte.parseByte() espera valores entre -128 e 127.
                numericBytes[i] = Byte.parseByte(trimmedByteString);
            }

            byte[] dataToWrite = numericBytes;

            paddedData = padDataTo4Bytes(dataToWrite);

            manageGPO(uid, (byte) 0x00, nfcv);

            byte[] writeCommand = createWriteCommand(uid, blockAddress, paddedData);

            byte[] response = nfcv.transceive(writeCommand);
            if (response[0] == 0) {
                Log.d("NFC", "Escrita realizada com sucesso ");
            } else {
                Log.d("NFC", "Erro ao realizar escrita : " + bytesToHex(response));
            }
            Thread.sleep(50);

            manageGPO(uid, (byte) 0x01, nfcv);

            nfcv.close();
        } catch (Exception e) {
            Log.e("NFC", "Escrita com erro: " + e.toString());
        }
    }

    private byte[] padDataTo4Bytes(byte[] data) {
        byte[] paddedData = new byte[5];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        return paddedData;
    }

    private static byte[] incrementLittleEndian(byte[] byteArray) {
        if (byteArray == null || byteArray.length != 4) {
            throw new IllegalArgumentException("O array de entrada deve ter exatamente 4 bytes.");
        }

        byte[] result = Arrays.copyOf(byteArray, byteArray.length);

        for (int i = 0; i < result.length; i++) {
            // Incrementa o byte atual
            // (result[i] & 0xFF) trata o byte como um valor não assinado (0-255)
            // antes de adicionar 1. O cast para byte lida com o overflow (255 + 1 = 0).
            result[i] = (byte) ((result[i] & 0xFF) + 1);

            // Se o byte não estourou (não voltou para 0 após o incremento),
            // significa que não precisamos carregar para o próximo byte.
            if (result[i] != 0) {
                break; // Incremento concluído
            }
            // Se result[i] == 0, o incremento causou um overflow neste byte,
            // então continuamos o loop para incrementar o próximo byte (carry).
        }
        return result;
    }

}