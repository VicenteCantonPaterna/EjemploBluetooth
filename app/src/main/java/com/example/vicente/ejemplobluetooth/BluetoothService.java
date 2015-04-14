package com.example.vicente.ejemplobluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Vicente on 10/03/2015.
 */
public class BluetoothService extends Thread {

    public static final int MSG_CONEXION = 0;
    public static final int MSG_RECIBIR = 1;

    private final Handler handler;
    private final BluetoothAdapter bAdapter;

    private BluetoothServer bServer;
    private BluetoothClient bClient;
    private BluetoothConnection bConnect;

    public BluetoothService(Handler handler, BluetoothAdapter bAdapter){

        this.handler = handler;
        this.bAdapter = bAdapter;

    }

    public String getNombreDispositivo()
    {
        return bConnect.socket.getRemoteDevice().getName();
    }

    public synchronized void iniciarConexion(){

        // Si se esta intentando realizar una conexion mediante un hilo cliente,
        // se cancela la conexion

        if(bClient != null)
        {
            bClient.cancelarConexion();
            bClient = null;
        }

        // Si existe una conexion previa, se cancela
        if(bConnect != null)
        {
            bConnect.cancelarConexion();
            bConnect = null;
        }

        // Arrancamos el hilo servidor para que empiece a recibir peticiones
        // de conexion
        if(bServer == null)
        {
            bServer = new BluetoothServer();
            bServer.start();
        }
    }

    public void finalizarServicio()
    {

        if(bClient != null)
            bClient.cancelarConexion();
        if(bConnect != null)
            bConnect.cancelarConexion();
        if(bServer != null)
            bServer.cancelarConexion();

        bClient = null;
        bConnect = null;
        bServer = null;

    }

    // Instancia un hilo conector
    public synchronized void solicitarConexion(BluetoothDevice dispositivo)
    {

        if(bClient != null)
        {
            bClient.cancelarConexion();
            bClient = null;
        }


        // Si existia una conexion abierta, se cierra y se inicia una nueva
        if(bConnect != null)
        {
            bConnect.cancelarConexion();
            bConnect = null;
        }

        // Se instancia un nuevo hilo conector, encargado de solicitar una conexion
        // al servidor, que sera la otra parte.
        bClient = new BluetoothClient(dispositivo);
        bClient.start();

    }

    public synchronized void realizarConexion(BluetoothSocket socket)
    {
        bConnect = new BluetoothConnection(socket);
        bConnect.start();
    }

    // Sincroniza el objeto con el hilo HiloConexion e invoca a su metodo escribir()
    // para enviar el mensaje a traves del flujo de salida del socket.
    public int enviar(byte[] buffer)
    {

        BluetoothConnection tmpConexion;

        synchronized(this) {
            tmpConexion = bConnect;
        }

        tmpConexion.escribir(buffer);

        return buffer.length;
    }

    public int recibir(String msg){
        return Integer.parseInt(msg);
    }

    public class BluetoothClient extends Thread {

        private static final String TAG = "En cliente: ";
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice device;
        private UUID uuid;

        public BluetoothClient(BluetoothDevice device){

            BluetoothSocket tmp = null;
            this.device = device;
            uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

            // Se genera el socket para el BluetoothDevice seleccionado

            try {
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmSocket = tmp;
        }

        public void run(){

            if (bAdapter.isDiscovering())
                bAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG,"Error abriendo el socket");
                try {
                    mmSocket.close();
                } catch (IOException inner) {
                    Log.e(TAG, "Error cerrando el socket", inner);
                }
            }

            // Reiniciamos el hilo cliente, ya que no lo necesitaremos mas
            synchronized(BluetoothService.this)
            {
                bClient = null;
            }

            // Realizamos la conexion
            realizarConexion(mmSocket);
        }

        public void cancelarConexion()
        {
            try {
                mmSocket.close();
            }
            catch(IOException e) {
                Log.e(TAG, "HiloCliente.cancelarConexion(): Error al cerrar el socket", e);
            }
        }
    }


    public class BluetoothServer extends Thread {

        private BluetoothServerSocket serverSocket;

        public BluetoothServer(){

            BluetoothServerSocket tmpServerSocket = null;

            // Creamos un socket para escuchar las peticiones de conexion
            try {
                tmpServerSocket = bAdapter.listenUsingRfcommWithServiceRecord("AppEjemplo",
                        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            } catch(IOException e) {
                e.printStackTrace();
            }

            serverSocket = tmpServerSocket;
        }

        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }
                if (socket != null) {
                    realizarConexion(socket);
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancelarConexion()
        {
            try {
                serverSocket.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BluetoothConnection extends Thread {

        private final BluetoothSocket socket;			// Socket
        private final InputStream inputStream;	// Flujo de entrada (lecturas)
        private final OutputStream outputStream;	// Flujo de salida (escrituras)

        public BluetoothConnection(BluetoothSocket socket){

            this.socket = socket;

            setName(socket.getRemoteDevice().getName() + "[ "+socket.getRemoteDevice().getAddress());

            InputStream tmpInputStream = null;
            OutputStream tmpOutputStream = null;

            try {
                tmpInputStream = socket.getInputStream();
                tmpOutputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tmpInputStream;
            outputStream = tmpOutputStream;
            handler.sendEmptyMessage(MSG_CONEXION);
        }

        // Metodo principal del hilo, encargado de realizar las lecturas
        public void run()
        {
            byte[] buffer = new byte[1024];
            int bytes;
            // Mientras se mantenga la conexion el hilo se mantiene en espera ocupada
            // leyendo del flujo de entrada
            while(true)
            {
                try {
                    // Leemos del flujo de entrada del socket
                    bytes = inputStream.read(buffer);

                    // Enviamos la informacion a la actividad a traves del handler.
                    // El metodo handleMessage sera el encargado de recibir el mensaje
                    // y mostrar los datos recibidos en el TextView
                    handler.obtainMessage(MSG_RECIBIR, bytes, -1, buffer).sendToTarget();
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void escribir(byte[] buffer)
        {
            try {
                // Escribimos en el flujo de salida del socket
                outputStream.write(buffer);

            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }

        public void cancelarConexion()
        {
            try {
                // Forzamos el cierre del socket
                socket.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }

    }

}
