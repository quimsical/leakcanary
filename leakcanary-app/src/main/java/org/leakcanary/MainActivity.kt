package org.leakcanary

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.leakcanary.internal.HeapDataRepository
import org.leakcanary.ui.theme.MyApplicationTheme
import shark.SharkLog

class MainActivity : ComponentActivity() {

  private var heapDataRepository: HeapDataRepository? = null

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      heapDataRepository = HeapDataRepository.Stub.asInterface(service)
    }

    override fun onServiceDisconnected(name: ComponentName) {
      heapDataRepository = null
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val intent = Intent("org.leakcanary.internal.HeapDataRepositoryService.BIND")
      .apply {
        setPackage("com.example.leakcanary")
      }
    val bringingServiceUp = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

    SharkLog.d { "HeapDataRepositoryService up=$bringingServiceUp" }

    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Greeting("Android") {
            if (heapDataRepository == null) {
              SharkLog.d { "no service" }
            }
            heapDataRepository?.sayHi()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindService(serviceConnection)
  }
}

@Composable
fun Greeting(name: String, onClick: () -> Unit) {
  Column {
    Text(text = "Hello $name!")
    Button(onClick = onClick) {
      Text(text = "Click count ?")
    }
  }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  MyApplicationTheme {
    Greeting("Android") {}
  }
}
