package com.example.connectdevice.ui.bluetooth;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothViewModel extends ViewModel {

    private final MutableLiveData<List<String>> itens = new MutableLiveData<>();
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public BluetoothViewModel() {
        listaDispositivos();
    }

    public LiveData<List<String>> getItens() {
        return itens;
    }

    @SuppressLint("MissingPermission")
    public void listaDispositivos() {

        List<String> listDavices = new ArrayList<>();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                listDavices.add(device.getName());
            }
        } else {
            listDavices.add("Bluetooth não ativado");
        }

        itens.setValue(listDavices);
    }
}