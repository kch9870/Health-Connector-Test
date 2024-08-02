package com.example.healthconnectortest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectortest.ui.theme.HealthConnectorTestTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private var healthConnectClient: HealthConnectClient? = null

    val START_TIME = Instant.now().minus(1, ChronoUnit.HOURS) // 예시: 2023년 7월 1일 오전 10시 (UTC 시간)
    val END_TIME = Instant.now() // 예시: 2023년 7월 1일 오전 11시 (UTC 시간)
    val START_ZONE_OFFSET = ZoneOffset.UTC // UTC 시간대 오프셋
    val END_ZONE_OFFSET = ZoneOffset.UTC // UTC 시간대 오프셋

    // 퍼미션 관리
    private val PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            HealthConnectorTestTheme {
                val scope = rememberCoroutineScope()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        insertStepData = {
                            scope.launch {
                                insertSteps(healthConnectClient!!)
                            }
                        },
                        readStepData = {
                            scope.launch {
                                readStepsByTimeRange(healthConnectClient!!, START_TIME, END_TIME)
                            }
                        },
                        updateStepData = {
                            scope.launch {
                                updateSteps(healthConnectClient!!, START_TIME, END_TIME)
                            }
                        },
                        insertHeartRateData = {
                            scope.launch {
                                insertHeartRate(healthConnectClient!!)
                            }
                        },
                        readHeartRateData = {
                            scope.launch {
                                readHeartRate(healthConnectClient!!, START_TIME, END_TIME)
                            }
                        }
                    )
                }
            }
        }

        getHealthConnector()

        lifecycleScope.launch {
            checkPermission()
        }
    }

    /**
     * 헬스 커넥트 클라이언트 가져오기
     *
     * 앱이 헬스 커넥트 앱의 데이터 스토어를 사용할 수 있도록 제공.
     * getSdkStatus 를 사용하여 헬스 커넥트가 설치되어 있는지 확인
     */
    private fun getHealthConnector() {
        val providerPackageName = "com.google.android.apps.healthdata"

        val availabilityStatus =
            HealthConnectClient.getSdkStatus(applicationContext, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            return // early return as there is no viable integration
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            // Optionally redirect to package installer to find a provider, for example:

            val uriString =
                "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            this.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", applicationContext.packageName)
                }
            )
            return
        }
        healthConnectClient = HealthConnectClient.getOrCreate(this)
    }

    /**
     * 사용자 권한 체크
     *
     * registerForActivityReuslt
     */
    private suspend fun checkPermission() {
        // Create the permissions launcher
        val requestPermissionActivityContract =
            PermissionController.createRequestPermissionResultContract()

        val requestPermissions =
            registerForActivityResult(requestPermissionActivityContract) { granted ->
                if (granted.containsAll(PERMISSIONS)) {
                    Toast.makeText(this, "앱을 사용하는 권한이 설정되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    // Lack of required permissions
                    Toast.makeText(this, "앱을 사용하는 권한이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
                }
            }

        lifecycleScope.launch {
            val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                // Permissions already granted; proceed with inserting or reading data
            } else {
                requestPermissions.launch(PERMISSIONS)
            }
        }
    }

    /**
     * 걸음 수 - 데이터 쓰기
     */
    private suspend fun insertSteps(healthConnectClient: HealthConnectClient) {
        try {
            val stepsRecord = StepsRecord(
                count = 100,
                startTime = START_TIME,
                endTime = END_TIME,
                startZoneOffset = START_ZONE_OFFSET,
                endZoneOffset = END_ZONE_OFFSET,
            )
            healthConnectClient.insertRecords(listOf(stepsRecord))
            Toast.makeText(this, "", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // Run error handling here
        }
    }

    /**
     * 걸음 수 - 데이터 읽기
     */
    private suspend fun readStepsByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {
            val response =
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
            /*for (stepRecord in response.records) {
                // Process each step record
            }*/

            Toast.makeText(this, "걸음 수 : ${response.records.last().count} 걸음", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // Run error handling here.
        }
    }

    /**
     * 걸음 수 - 데이터 업데이트
     *
     * 이 함수는 새로운 업데이트를 생성하는 것 같다. upsert를 써야될 것 같기도...
     */
    private suspend fun updateSteps(
        healthConnectClient: HealthConnectClient,
        prevRecordStartTime: Instant,
        prevRecordEndTime: Instant
    ) {
        try {
            val request = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        prevRecordStartTime,
                        prevRecordEndTime
                    )
                )
            )

            val newStepsRecords = arrayListOf<StepsRecord>()
            for (record in request.records) {
                // Adjusted both offset values to reflect changes
                val sr = StepsRecord(
                    count = 200,
                    startTime = record.startTime,
                    startZoneOffset = record.startTime.atZone(ZoneId.of("PST")).offset,
                    endTime = record.endTime,
                    endZoneOffset = record.endTime.atZone(ZoneId.of("PST")).offset,
                    metadata = record.metadata
                )
                newStepsRecords.add(sr)
            }

            healthConnectClient.updateRecords(newStepsRecords)
        } catch (e: Exception) {
            // Run error handling here
        }
    }

    /**
     * 심박수 쓰기
     */
    private suspend fun insertHeartRate(healthConnectClient: HealthConnectClient) {
        val heartRateRecord = HeartRateRecord(
            startTime = START_TIME,
            startZoneOffset = START_ZONE_OFFSET,
            endTime = END_TIME,
            endZoneOffset = END_ZONE_OFFSET,
            // records 10 arbitrary data, to replace with actual data
            samples = List(10) { index ->
                HeartRateRecord.Sample(
                    time = START_TIME + Duration.ofSeconds(index.toLong()),
                    beatsPerMinute = 100 + index.toLong(),
                )
            }
        )
        healthConnectClient.insertRecords(listOf(heartRateRecord))
        Toast.makeText(this, "심박수 입력", Toast.LENGTH_SHORT).show()
    }

    /**
     * 심박수 읽기
     */
    private suspend fun readHeartRate(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {
            val response =
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )

            Toast.makeText(this, "심박수 : ${response.records.last().samples.last().beatsPerMinute} bpm", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // Run error handling here.
        }
    }

    /**
     * 센서 권한
     *
     * 센서 권한은 따로 설정 해야 한다.
     */
    private fun checkSensorPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            Toast.makeText(this, "센서 퍼미션 없음", Toast.LENGTH_SHORT).show()

            //ask for permission
            requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION))

        } else {
            Toast.makeText(this, "센서 퍼미션 있음", Toast.LENGTH_SHORT).show()
            initSensor()
            //권한있는 경우
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {

        var results = true
        it.values.forEach {
            if (!it) {
                results = false
                return@forEach
            }
        }
        Log.d("TEST_LOG", "requestPermissionLauncher() | result: $results")

        if (!results) {
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        } else {
            initSensor()
            //모두 권한이 있을 경우
        }
    }

    private fun initSensor() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor != null) {  Toast.makeText(this, "심박수 센서 존재", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "심박수 센서가 없음.", Toast.LENGTH_SHORT).show()
        }

        val heartRateListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // 센서 정확도 변경 시 처리
            }

            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
                        val heartRate = event.values[0].toInt()
                        // 심박수 값을 이용하여 UI 업데이트 등 원하는 작업 수행
                        runOnUiThread {
                            // UI 스레드에서 UI 업데이트
                            Toast.makeText(this@MainActivity, "심박수: $heartRate", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }

        sensorManager.registerListener(
            heartRateListener,
            heartRateSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

}

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    insertStepData: () -> Unit,
    readStepData: () -> Unit,
    updateStepData: () -> Unit,
    insertHeartRateData: () -> Unit,
    readHeartRateData: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(150.dp)
                    .height(80.dp),
                onClick = {
                    insertStepData()
                }) {
                Text(text = "데이터 쓰기")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(150.dp)
                    .height(80.dp),
                onClick = {
                    readStepData()
                }) {
                Text(text = "데이터 읽기")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(150.dp)
                    .height(80.dp),
                onClick = {
                    updateStepData()
                }) {
                Text(text = "데이터 업데이트")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(150.dp)
                    .height(80.dp),
                onClick = {
                    insertHeartRateData()
                }) {
                Text(text = "심박수 쓰기")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                modifier = Modifier
                    .width(150.dp)
                    .height(80.dp),
                onClick = {
                    readHeartRateData()
                }) {
                Text(text = "심박수 읽기")
            }
        }
    }
}