package com.example.connectdevice.ui.nfc;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.connectdevice.MainActivity;
import com.example.connectdevice.databinding.FragmentNfcBinding;

import java.util.ArrayList;

import android.widget.Switch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NfcFragment extends Fragment {

    private FragmentNfcBinding binding;
    private NfcAdapter nfcAdapter;
    private NfcViewModel nfcViewModel;
    private EditText inputMessage;
    private EditText inputAddress;
    private Button btnWrite;
    private Button btnClear;
    private ListView addressListView;
    private ArrayList<String> addressList;
    private ArrayAdapter<String> adapter;
    private NfcV nfcv;
    private boolean writeMultipleBlocs;
    private Switch aSwitch;
    private Switch bSwitch;
    private final Handler mainUiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        nfcViewModel = new ViewModelProvider(this).get(NfcViewModel.class);

        binding = FragmentNfcBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext());

        // Verifica se o dispositivo tem suporte a NFC
        if (nfcAdapter == null) {
            Log.e("NFC", "NFC não está disponível neste dispositivo.");
            return root;
        }

        inputMessage = binding.inputMessage;
        inputAddress = binding.inputAddress;
        btnWrite = binding.btnWrite;
        addressListView = binding.addressListView;
        addressList = new ArrayList<>();
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, addressList);
        addressListView.setAdapter(adapter);
        btnClear = binding.btnClear;
        aSwitch = binding.switch1;
        bSwitch = binding.switch2;

        populateAddressList();

        aSwitch.setOnClickListener(view -> {
            if (aSwitch.isChecked()) {
                bSwitch.setVisibility(View.VISIBLE);
            } else {
                bSwitch.setVisibility(View.INVISIBLE);
            }
        });

        btnWrite.setOnClickListener(v -> {
            btnWrite.setEnabled(false);
            String addressStr = binding.inputAddress.getText().toString();
            String valueStr = binding.inputMessage.getText().toString();

            if (addressStr.isEmpty() || valueStr.isEmpty()) {
                Log.e("NFC", "Endereço ou valor vazio!");
                return;
            }

            int intAdress = Integer.parseInt(addressStr);
            byte blockAddress = (byte) intAdress;  // Exemplo: bloco 0
            // int intValue = Integer.parseInt(valueStr); // Converte a string para inteiro
            //byte valueToWrite = (byte) intValue;

            Log.i("NFC", "Tentando escrever na tag NFC no endereço: " + blockAddress + " com o valor: " + valueStr);

            // Verifica tag detectada
            if (getActivity() instanceof MainActivity) {

                MainActivity activity = (MainActivity) getActivity();
                Intent intent = activity.getIntent();

                if (intent != null) {

                    Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                    if (tag != null) {
                        NfcV nfcv = NfcV.get(tag);

                        if (aSwitch.isChecked() && !bSwitch.isChecked()) {
                            nfcViewModel.writeMultipleBlocks(nfcv, blockAddress, valueStr,false);

                        } else if (aSwitch.isChecked() && bSwitch.isChecked()) {

                            backgroundExecutor.execute(() -> { // (2)
                                try {
                                    int count = 0;

                                    while (bSwitch.isChecked() && count < 11) {

                                        populateAddressList();

                                        if (!nfcViewModel.StatusRead) {
                                            try {
                                                nfcViewModel.writeMultipleBlocks(nfcv, blockAddress, valueStr, true);
                                                Thread.sleep(3000);
                                            } catch (Exception e) {
                                                Log.e("NFC_LOOP", " writeMultipleBlocks Exception: " + e.getMessage());
                                            }
                                            count++;
                                        }


                                        if (count == 10) {
                                            mainThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    bSwitch.setChecked(false);
                                                }
                                            });
                                            count = 0;
                                        }


                                    }

                                } catch (Exception e) {
                                    Log.e("NFC_LOOP", "Exception: " + e.toString());
                                }
                            });


                        } else {
                            nfcViewModel.writeSingleBlock(nfcv, blockAddress, valueStr);
                        }

                    } else {
                        Log.e("NFC", "Nenhuma tag NFC detectada!");
                    }
                }
            }
            if (!bSwitch.isChecked())
                populateAddressList();

            btnWrite.setEnabled(true);
        });

        btnClear.setOnClickListener(v -> {

            byte blockAddress = (byte) 0;

            // Verifica tag detectada
            if (getActivity() instanceof MainActivity) {

                MainActivity activity = (MainActivity) getActivity();
                Intent intent = activity.getIntent();

                if (intent != null) {

                    Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                    if (tag != null) {

                        String[] techList = tag.getTechList();
                        for (String tech : techList) {
                            Log.i("NFC", "Tecnologia suportada: " + tech);
                        }

                        NfcV nfcv = NfcV.get(tag);

                        nfcViewModel.clear(nfcv);


                    } else {
                        Log.e("NFC", "Nenhuma tag NFC detectada!");
                    }
                }
            }

            if (!bSwitch.isChecked())
                populateAddressList();
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcViewModel != null) {
            PendingIntent pendingIntent = nfcViewModel.getPendingIntent(requireContext());
            IntentFilter[] intentFilters = nfcViewModel.getIntentFilters();

            if (pendingIntent != null && intentFilters != null) {
                nfcAdapter.enableForegroundDispatch(getActivity(), pendingIntent, intentFilters, null);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(getActivity());
        }
    }

    private void populateAddressList() {
        try {

            nfcViewModel.StatusRead = true;

            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                Intent intent = activity.getIntent();
                Log.d("NFC", "1");
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                NfcV nfcv = NfcV.get(tag);
                Log.d("NFC", "2");
                if (tag == null) {
                    Log.e("NFC", "Tag NFC não encontrada!");
                    return;
                }
                Log.d("NFC", "3");
                if (nfcv != null) {

                    Log.d("NFC", "4");
                    if (!nfcv.isConnected()) {
                        nfcv.close();
                        nfcv.connect();
                    }
                    Log.d("NFC", "5");
                    addressList.clear(); // Limpa a lista antes de preencher
                    StringBuilder valorSemAddress = new StringBuilder();
                    Log.d("NFC", "6");
                    addressList.addAll(nfcViewModel.readMultipleBlocks(nfcv, (byte) 0, 64));
                    // Atualiza a ListView
                    Log.d("NFC", "7");
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });
                    Log.d("NFC", "8");
                    nfcv.close();
                    Log.d("NFC", "9");
                }
            }

            nfcViewModel.StatusRead = false;
        } catch (Exception e) {
            nfcViewModel.StatusRead = false;
            Log.e("NFC", "populateAddressList Exception: " + e.getMessage());
        }
    }


}