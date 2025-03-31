package com.example.connectdevice.ui.bluetooth;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.connectdevice.databinding.FragmentBluetoothBinding;
import java.util.List;

public class BluetoothFragment extends Fragment {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 1001;
    private FragmentBluetoothBinding binding;
    private ListView listView;
    private ArrayAdapter<String> adapter;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentBluetoothBinding.inflate(inflater, container, false);
        listView = binding.listDevice;

        View root = binding.getRoot();

        if (temPermBluetooth()) {
            BluetoothViewModel bluetoothViewModel =
                    new ViewModelProvider(this).get(BluetoothViewModel.class);

            bluetoothViewModel.getItens().observe(getViewLifecycleOwner(), this::atualizarLista);
        } else {
            solicitaPermissao();
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void atualizarLista(List<String> itens) {
        if (adapter == null) {
            adapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_list_item_1, itens);
            listView.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(itens);
            adapter.notifyDataSetChanged();
        }
    }

    private boolean temPermBluetooth() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void solicitaPermissao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permission, @NonNull int[] grandResults) {
        super.onRequestPermissionsResult(requestCode, permission, grandResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grandResults.length > 0 && grandResults[0] == PackageManager.PERMISSION_GRANTED) {
                BluetoothViewModel bluetoothViewModel =
                        new ViewModelProvider(this).get(BluetoothViewModel.class);

                bluetoothViewModel.getItens().observe(getViewLifecycleOwner(), this::atualizarLista);
            }
        }
    }
}