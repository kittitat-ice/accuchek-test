package com.example.accu_check_test

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accu_check_test.adapters.GlucoseRecordsAdapter
import com.example.accu_check_test.data.GlucoseRecord
import com.example.accu_check_test.databinding.FragmentFirstBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

  private var _binding: FragmentFirstBinding? = null

  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding get() = _binding!!

  private var accuChekDevice: BluetoothDevice? = null
  private val bluetoothManager by lazy { requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
  private val bluetoothAdapter by lazy { bluetoothManager.adapter }
  private val glucoseRecordsAdapter by lazy { GlucoseRecordsAdapter(mutableListOf()) }

  private val accuCheckAddress = "6C:C3:74:CB:15:EE"
  private val UUID_GLUCOSE_SERVICE = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
  private val UUID_GLUCOSE_MEASUREMENT = UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb")
  private val UUID_RACP =
    UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb") // record access control point
  private val UUID_CLIENT_CHARACTERISTIC_CONFIG =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

  private var currentStep = 0

  private val STEP_WRITE_DESC_RACP = 5
  private val STEP_WRITE_DESC_GLC = 6
  private val STEP_WRITE_CMD_RACP_READ_ALL_DATA = 7

  private var count = 0

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {

    _binding = FragmentFirstBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    ActivityCompat.requestPermissions(
      requireActivity(),
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
      ),
      1
    )

    setupIntentFilter()
    setupViewListener()
    setupAdapter()
  }

  private fun setupAdapter() {
    binding.rvData.adapter = glucoseRecordsAdapter
    binding.rvData.layoutManager = LinearLayoutManager(requireContext())
  }

  private fun setupViewListener() {
    binding.cvTestButton1.setOnClickListener {
      startBTDiscovery()
    }
    binding.btTest1.setOnClickListener {
      val device = accuChekDevice ?: bluetoothAdapter.getRemoteDevice(accuCheckAddress)
      if (accuChekDevice == null) accuChekDevice = device
      Log.e("DEVICE", "${device.name} -> ${device.address}")

      if (android.os.Build.VERSION.SDK_INT == 31 && ActivityCompat.checkSelfPermission(
          requireContext(),
          Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        ActivityCompat.requestPermissions(
          requireActivity(),
          arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT
          ),
          2
        )
      }


      connectDevice(device)
      /*
      when (device.bondState) {
        BluetoothDevice.BOND_BONDED -> {
          Log.e("BondState", "Bonded")
          connectDevice(device)
        }
        BluetoothDevice.BOND_NONE -> {
          Log.e("BondState", "Bond None")
          device.createBond()
        }
        BluetoothDevice.BOND_BONDING -> Log.e("BondState", "Bonding")
      }
      */
    }
  }

  private fun setupIntentFilter() {
    val bluetoothFoundFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    requireActivity().registerReceiver(bluetoothFoundReceiver, bluetoothFoundFilter)
    val bluetoothBondedFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    requireActivity().registerReceiver(bluetoothBondedReceiver, bluetoothBondedFilter)
  }

  private fun connectDevice(device: BluetoothDevice) {
    if (android.os.Build.VERSION.SDK_INT == 31 && ActivityCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.BLUETOOTH_CONNECT
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        requireActivity(),
        arrayOf(
          Manifest.permission.BLUETOOTH_CONNECT
        ),
        2
      )
    }
    count = 0
    currentStep = 0
    glucoseRecordsAdapter.clearList()

    binding.tvName.text = device.name
    binding.tvAddress.text = device.address
    device.connectGatt(requireContext(), false, bluetoothGattCallback)
  }

  private val bluetoothGattCallback = object : BluetoothGattCallback() {
    val TAG_GATT = "GATT"
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      when (newState) {
        BluetoothProfile.STATE_CONNECTED -> {
          Log.e(TAG_GATT, "onConnectionStateChange -> STATE_CONNECTED")
          gatt?.discoverServices() ?: Log.e(TAG_GATT, "GATT IS NULL ???")
        }
        BluetoothProfile.STATE_DISCONNECTED -> {
          Log.e(TAG_GATT, "onConnectionStateChange -> STATE_DISCONNECTED")
        }
        BluetoothProfile.STATE_CONNECTING -> Log.e(
          TAG_GATT,
          "onConnectionStateChange -> STATE_CONNECTING"
        )
      }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      super.onServicesDiscovered(gatt, status)
      when (status) {
        BluetoothGatt.GATT_SUCCESS -> {
          Log.e(TAG_GATT, "onServicesDiscovered -> GATT_SUCCESS")
          gatt?.let {
            for (service in it.services) {
              Log.e(TAG_GATT, "Service list -> uuid ${service.uuid} -> ${service.type}")
              if (service.uuid == UUID_GLUCOSE_SERVICE) {
                Log.e("HAHAHA", "GLUCOSE Service found")

                val racp: BluetoothGattCharacteristic = service.getCharacteristic(UUID_RACP)
                it.setCharacteristicNotification(racp, true)
                val glc: BluetoothGattCharacteristic =
                  service.getCharacteristic(UUID_GLUCOSE_MEASUREMENT)

                it.setCharacteristicNotification(glc, true)

                val descriptor = racp.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG)
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                it.writeDescriptor(descriptor)
                currentStep = STEP_WRITE_DESC_RACP

//                for (chara in service.characteristics) {
//                  Log.e(TAG_GATT, "Characteristic list -> uuid ${chara.uuid} -> ${chara.value}")
//                  if (chara.uuid == UUID_GLUCOSE_MEASUREMENT) {
//                    Log.e("HAHAHA", "GLUCOSE Chara found")
//                    it.setCharacteristicNotification(chara, true)
////                    val descriptor = chara.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG)
////                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
////                    it.writeDescriptor(descriptor)
//                    //it.readCharacteristic(chara)
//                  } else if (chara.uuid == UUID_RACP) {
//                    Log.e("HAHAHA", "RACP Chara found")
//                    it.setCharacteristicNotification(chara, true)
//                    val command = byteArrayOf(0x0101.toByte())
//                    chara.setValue(command)
//                  }
//                }
                break
              }
            }
          } ?: Log.e(TAG_GATT, "GATT IS NULL ???")
        }
        BluetoothGatt.GATT_FAILURE -> Log.e(TAG_GATT, "onServicesDiscovered -> GATT_FAILURE")
      }
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt?,
      characteristic: BluetoothGattCharacteristic?,
    ) {
      super.onCharacteristicChanged(gatt, characteristic)
      Log.e(TAG_GATT, "onCharacteristicChanged")
      characteristic?.let { chara ->
        if (chara.uuid == UUID_GLUCOSE_MEASUREMENT) {
          count++
          Log.d(TAG_GATT, "DATA COUNT >>>>>>>>>>>>> $count")
          convertValue(chara)
        }
      } ?: Log.e(TAG_GATT, "onCharacteristicChanged -> CHARA IS NULL")
    }

    override fun onDescriptorWrite(
      gatt: BluetoothGatt?,
      descriptor: BluetoothGattDescriptor?,
      status: Int
    ) {
      super.onDescriptorWrite(gatt, descriptor, status)
      Log.e(TAG_GATT, "onDescriptorWrite")
      when (currentStep) {
        STEP_WRITE_DESC_RACP -> {
          Log.e(TAG_GATT, "STEP_WRITE_DESC_RACP")
          val glc = gatt?.getService(UUID_GLUCOSE_SERVICE)?.getCharacteristic(UUID_GLUCOSE_MEASUREMENT) ?: return
          val descriptor = glc.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG)
          descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
          gatt.writeDescriptor(descriptor)
          currentStep = STEP_WRITE_DESC_GLC
        }
        STEP_WRITE_DESC_GLC -> {
          Log.e(TAG_GATT, "STEP_WRITE_DESC_GLC")
          // https://www.bluetooth.com/wp-content/uploads/2020/12/WENS_Wearable_Exposure_Notification_Service_Draft_Specification.pdf
          val command = byteArrayOf(
            0x01.toByte(), // Opcode 0x01 = report stored records
            0x01.toByte()  // Operator 0x01 = all data
          )
          val racp = gatt?.getService(UUID_GLUCOSE_SERVICE)?.getCharacteristic(UUID_RACP) ?: return
          racp.value = command
          gatt.writeCharacteristic(racp)
          currentStep = STEP_WRITE_CMD_RACP_READ_ALL_DATA
        }
      }

    }

    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int
    ) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.e(TAG_GATT, "onCharacteristicRead -> GATT_SUCCESS")
        Log.e(TAG_GATT, "chara -> ${characteristic.getStringValue(0)}")
      } else {
        Log.e(TAG_GATT, "onCharacteristicRead -> ELSE")
      }
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt?,
      characteristic: BluetoothGattCharacteristic?,
      status: Int
    ) {
      super.onCharacteristicWrite(gatt, characteristic, status)
      Log.e(TAG_GATT, "onCharacteristicWrite")
      Log.e(
        TAG_GATT,
        "${characteristic?.uuid} | ${
          characteristic?.value?.joinToString(separator = " ") {
            String.format("%02X", it)
          }
        }"
      )
    }
  }

  private fun convertValue(characteristic: BluetoothGattCharacteristic) {
    val TAG_CONVERT = "ConvertVal"
    when (characteristic.uuid) {
      UUID_GLUCOSE_MEASUREMENT -> {
        val flag = characteristic.properties
        val format = when (flag and 0x01) {
          0x01 -> {
            Log.d(TAG_CONVERT, "Glucose format UINT16.")
            BluetoothGattCharacteristic.FORMAT_UINT16
          }
          else -> {
            Log.d(TAG_CONVERT, "Glucose format UINT8.")
            BluetoothGattCharacteristic.FORMAT_UINT8
          }
        }
        val glucose = characteristic.getIntValue(format, 12)
        val seq1 = characteristic.getIntValue(format, 1)
        val seq2 = characteristic.getIntValue(format, 2)
        val seq = seq1 + seq2

        val gluRecord = GlucoseRecord(seq, glucose)

        lifecycleScope.launch(Dispatchers.Main) {
          glucoseRecordsAdapter.addToList(gluRecord)
        }

        Log.d(TAG_CONVERT, "value -> $glucose")
        val data: ByteArray? = characteristic.value
        if (data?.isNotEmpty() == true) {
          val hexString: String = data.joinToString(separator = " ") {
            String.format("%02X", it)
          }
          Log.d(TAG_CONVERT, "RAW -> $hexString")
        }
        /*
          raw hex: 0B 13 00 E4 07 02 0A 08 20 05 2F 00 5B B0 F8 00 00
          ค่าล่าสุดในเครื่อง: 91 mg/dL 10/02/20 9:19
          แปลงค่าหน้า 26: https://www.silabs.com/documents/public/application-notes/AN982-Bluetooth-LE-Glucose-Sensor.pdf
             0      1    2       3  4    5     6      7      8     9     10 11       12       13      14     15 16
           flag   seq number    year   month  day   hour?  min?  sec?   offset?    glucose   unit?    ?      status
            0B      13  00      E4 07   02    0A     08     20    05     2F 00       5B       B0      F8     00 00
            0B      04  00      E2 07   09    07      02    3B    04     A5 01       61       B0      F8     00 00
         */
      }
      else -> {
        // For all other profiles, writes the data formatted in HEX.
        val data: ByteArray? = characteristic.value
        if (data?.isNotEmpty() == true) {
          val hexString: String = data.joinToString(separator = " ") {
            String.format("%02X", it)
          }
          Log.d(TAG_CONVERT, "ELSE -> $hexString")
        }
      }
    }
  }

  private fun startBTDiscovery() {
    if (android.os.Build.VERSION.SDK_INT == 31 && ActivityCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.BLUETOOTH_SCAN
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        requireActivity(),
        arrayOf(
          Manifest.permission.BLUETOOTH_SCAN
        ),
        2
      )
    }
    if (bluetoothAdapter.isDiscovering) {
      bluetoothAdapter.cancelDiscovery()
    }
    bluetoothAdapter.startDiscovery()
  }

  private fun stopBTDiscovery() {
    bluetoothAdapter.cancelDiscovery()
  }

  private val bluetoothFoundReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        BluetoothDevice.ACTION_FOUND -> {
          // Discovery has found a device. Get the BluetoothDevice
          // object and its info from the Intent.
          val device: BluetoothDevice =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return

          Log.e("FOUND", "Device Name -> ${device.name} | Address -> ${device.address}")
//          if (device.address == accuCheckAddress) {
//            accuChekDevice = device
//            stopBTDiscovery()
//          }
        }
      }
    }
  }

  private val bluetoothBondedReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
          // Discovery has found a device. Get the BluetoothDevice
          // object and its info from the Intent.
          val state = intent.extras?.get(BluetoothDevice.EXTRA_BOND_STATE) as Int
          if (state == BluetoothDevice.BOND_BONDED) {
            val device: BluetoothDevice =
              intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            connectDevice(device)
          }
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null

    requireActivity().unregisterReceiver(bluetoothFoundReceiver)
    requireActivity().unregisterReceiver(bluetoothBondedReceiver)
    stopBTDiscovery()
  }
}