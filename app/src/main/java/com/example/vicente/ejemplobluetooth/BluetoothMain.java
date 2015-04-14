package com.example.vicente.ejemplobluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;


public class BluetoothMain extends ActionBarActivity implements View.OnClickListener {

    private BluetoothAdapter bAdapter;
    private BluetoothService bService;
    private BluetoothDevice dispositivo;
    private BluetoothDeviceArrayAdapter bArrayAdapter;
    private ArrayList<BluetoothDevice> arrayDevices;
    private String mensaje;

    private Button conectar;
    private Button lanzarServidor;
    private ListView lvDispositivos;
    private EditText textEnviar;
    private Button btnEnviar;
    private TextView viewMensaje;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        conectar=(Button)findViewById(R.id.btnConectar);
        conectar.setEnabled(false);
        lanzarServidor=(Button)findViewById(R.id.btnServidor);
        lvDispositivos=(ListView)findViewById(R.id.lvDispositivos);

        bAdapter = BluetoothAdapter.getDefaultAdapter();
        bService = new BluetoothService(handler,bAdapter);

        Set<BluetoothDevice> pairedDevices = bAdapter.getBondedDevices();
        arrayDevices = new ArrayList<>();

        // Se comprueba que haya algun dispositivo vinculado
        if (!pairedDevices.isEmpty()) {
            // Se introducen en el array adapter
            for (BluetoothDevice device : pairedDevices) {
                arrayDevices.add(device);
            }
            bArrayAdapter = new BluetoothDeviceArrayAdapter(this,android.R.layout.simple_list_item_2, arrayDevices);
            lvDispositivos.setAdapter(bArrayAdapter);
        }else{
            Toast.makeText(this.getBaseContext(),"Debes vincularte antes con un dispositivo", Toast.LENGTH_LONG);
        }

        lanzarServidor.setOnClickListener(this);
        conectar.setOnClickListener(this);
        lvDispositivos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                dispositivo = (BluetoothDevice) a.getItemAtPosition(position);
                conectar.setEnabled(true);
            }
        });
    }

    //Metodo para realizar la conexion con el dispositivo
    public void conectarDispositivo(String direccion)
    {
        Toast.makeText(this, "Conectando a " + direccion, Toast.LENGTH_LONG).show();
        if(bService != null)
        {
            BluetoothDevice dispositivoRemoto = bAdapter.getRemoteDevice(direccion);
            bService.solicitarConexion(dispositivoRemoto);
            this.dispositivo = dispositivoRemoto;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Handler que obtendrá informacion de BluetoothService
    private final Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            byte[] buffer = null;
            String mensaje = null;

            // Atendemos al tipo de mensaje
            switch (msg.what) {
                // Mensaje de lectura: se mostrara en el TextView
                case BluetoothService.MSG_RECIBIR: {

                    // Leemos del flujo de entrada del socket
                    buffer = (byte[])msg.obj;
                    mensaje = new String(buffer, 0, msg.arg1);
                    viewMensaje.setText(mensaje);

                    break;
                }

                // Mensaje de conexion: se cambia de layout
                case BluetoothService.MSG_CONEXION: {

                    setContentView(R.layout.activity_recepcion);
                    instanciarElementos();
                    break;
                }
            }
        }
    };

    public void instanciarElementos(){
        btnEnviar = (Button)findViewById(R.id.btnEnviar);
        btnEnviar.setOnClickListener(this);
        textEnviar = (EditText)findViewById(R.id.textEnviar);
        viewMensaje = (TextView)findViewById(R.id.viewRecepcion);
    }

    //Se implementa el OnClick de los botones
    @Override
    public void onClick(View view) {

        switch (view.getId()){
            case R.id.btnServidor:
                bService.iniciarConexion();
                break;

            case R.id.btnConectar:
                conectarDispositivo(dispositivo.getAddress());
                break;

            case R.id.btnEnviar:
                mensaje = textEnviar.getText().toString();
                bService.enviar(mensaje.getBytes());
                break;

        }
    }
}
