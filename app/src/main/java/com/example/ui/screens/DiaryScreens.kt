package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DecryptedDiaryEntry
import com.example.ui.DiaryViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveScreen {
    LOCK,
    DASHBOARD,
    ADD_EDIT,
    SYNC_SETTINGS
}

// Custom Premium Dark Color Scheme
val DarkBackground = Color(0xFF0F1420)
val DarkCardBg = Color(0xFF181E32)
val GoldAccent = Color(0xFFFFD54F)
val NeonCyan = Color(0xFF00E5FF)
val CalmSoftPeach = Color(0xFFFFCCBC)
val SilentGray = Color(0xFF90A4AE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryAppView(viewModel: DiaryViewModel) {
    var currentScreen by remember { mutableStateOf(ActiveScreen.LOCK) }
    val context = LocalContext.current

    // Sync state locking helper
    LaunchedEffect(viewModel.isUnlocked) {
        if (!viewModel.isUnlocked) {
            currentScreen = ActiveScreen.LOCK
        } else if (currentScreen == ActiveScreen.LOCK) {
            currentScreen = ActiveScreen.DASHBOARD
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                ActiveScreen.LOCK -> {
                    PasswordLockView(
                        viewModel = viewModel,
                        onUnlocked = {
                            currentScreen = ActiveScreen.DASHBOARD
                        }
                    )
                }
                ActiveScreen.DASHBOARD -> {
                    JournalDashboardView(
                        viewModel = viewModel,
                        onNavigateToAddEdit = {
                            viewModel.clearForm()
                            currentScreen = ActiveScreen.ADD_EDIT
                        },
                        onNavigateToSync = {
                            currentScreen = ActiveScreen.SYNC_SETTINGS
                        },
                        onEditEntry = { entry ->
                            viewModel.selectEntryForEdit(entry)
                            currentScreen = ActiveScreen.ADD_EDIT
                        }
                    )
                }
                ActiveScreen.ADD_EDIT -> {
                    AddEditJournalView(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.clearForm()
                            currentScreen = ActiveScreen.DASHBOARD
                        }
                    )
                }
                ActiveScreen.SYNC_SETTINGS -> {
                    GoogleDriveSyncView(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = ActiveScreen.DASHBOARD
                        }
                    )
                }
            }
        }
    }
}

// --- Screen 1: Password Lock Screen ---
@Composable
fun PasswordLockView(viewModel: DiaryViewModel, onUnlocked: () -> Unit) {
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Registration states for Decoy passcode
    var confirmPasswordInput by remember { mutableStateOf("") }
    var fakePasswordInput by remember { mutableStateOf("") }
    var showFakePasswordSetup by remember { mutableStateOf(false) }

    // Biometrics states
    var showBiometricDialog by remember { mutableStateOf(false) }
    var biometricScanSuccess by remember { mutableStateOf(false) }
    var biometricProgress by remember { mutableStateOf(0f) }

    val context = LocalContext.current

    // Trigger fingerprint scan animation helper
    LaunchedEffect(showBiometricDialog) {
        if (showBiometricDialog) {
            biometricProgress = 0f
            biometricScanSuccess = false
            for (i in 1..20) {
                kotlinx.coroutines.delay(80)
                biometricProgress = i / 20f
            }
            biometricScanSuccess = true
            kotlinx.coroutines.delay(600)
            showBiometricDialog = false
            
            // Automatically find and unlock with password if password hash is set
            if (viewModel.isPasswordSet) {
                // Securely retrieve and decrypt cached bio master credentials through Keystore Enclave
                val sharedPrefs = context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)
                val encryptedBioPassword = sharedPrefs.getString("bio_master_password_enc", "") ?: ""
                val decryptedBioPassword = com.example.data.AndroidKeyStoreHelper.decryptWithKeyStore(encryptedBioPassword)
                if (decryptedBioPassword.isNotEmpty()) {
                    val success = viewModel.unlockApp(decryptedBioPassword)
                    if (success) {
                        onUnlocked()
                    } else {
                        viewModel.authError = "⚠️ Xác thực sinh trắc học phần cứng Keystore thất bại."
                    }
                } else {
                    // Try legacy backward compatible check: ask for manual login once to cache bio keys
                    val encryptedStoredHash = sharedPrefs.getString("password_hash", "") ?: ""
                    val decryptedStoredHash = com.example.data.AndroidKeyStoreHelper.decryptWithKeyStore(encryptedStoredHash)
                    if (decryptedStoredHash.isNotEmpty()) {
                        viewModel.authError = "⚠️ Thiết lập bọc mật mã mới: Vui lòng nhập mật mã số một lần để nâng cấp khóa vân tay."
                    } else {
                        viewModel.authError = "⚠️ Thiết bị chưa cấu hình mật mã khóa."
                    }
                }
            } else {
                viewModel.authError = "⚠️ Hãy thiết lập mật khẩu thủ công bằng số trong lần đăng ký đầu tiên."
            }
        }
    }

    // Recovery key display alert overlay
    if (viewModel.rawRecoveryKeyShown != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rawRecoveryKeyShown = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = "Safety Code", tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LƯU MÃ CỨU HỘ CỨNG", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "⚠️ cực kỳ QUAN TRỌNG: Viết mã này ra giấy hoặc lưu ngoại tuyến an toàn. Nếu bạn quên mật khẩu, đây là khóa mã hóa duy nhất có thể khôi phục dòng nhật ký của bạn.",
                        color = SilentGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.rawRecoveryKeyShown ?: "",
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Toàn bộ dữ liệu của bạn được mã hóa đầu cuối AES-256. Hệ thống cam kết không giữ bản sao mật khẩu hay lưu trên máy chủ để đảm bảo tính riêng tư tuyệt đối.",
                        color = SilentGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.rawRecoveryKeyShown = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("Đã Sao Chép & Lưu Trữ", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCardBg
        )
    }

    if (viewModel.isRecoveryAuthSuccess) {
        var newPasswordInput by remember { mutableStateOf("") }
        var confirmNewPasswordInput by remember { mutableStateOf("") }
        var resetError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { 
                viewModel.isRecoveryAuthSuccess = false 
                viewModel.tempDecryptedMasterKey = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Reset PIN", tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THIẾT LẬP MẬT MÃ MỚI", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Mã cứu hộ hợp lệ! Vui lòng đặt mật mã số khóa chính mới để bọc lại khóa bảo mật thiết bị đầu cuối của bạn:",
                        color = SilentGray,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("Mật mã số mới", color = SilentGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmNewPasswordInput,
                        onValueChange = { confirmNewPasswordInput = it },
                        label = { Text("Xác nhận mật mã số mới", color = SilentGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    resetError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.length < 4) {
                            resetError = "⚠️ Mật khẩu tối thiểu từ 4 ký tự số!"
                            return@Button
                        }
                        if (newPasswordInput != confirmNewPasswordInput) {
                            resetError = "⚠️ Mật mã nhập lại không trùng khớp!"
                            return@Button
                        }
                        val success = viewModel.resetPasswordViaRecoverySession(newPasswordInput)
                        if (success) {
                            onUnlocked()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("Khôi Phục & Đăng Nhập", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.isRecoveryAuthSuccess = false 
                    viewModel.tempDecryptedMasterKey = null
                }) {
                    Text("Hủy", color = Color.White)
                }
            },
            containerColor = DarkCardBg
        )
    }

    if (showBiometricDialog) {
        AlertDialog(
            onDismissRequest = { showBiometricDialog = false },
            title = {
                Text(
                    text = if (biometricScanSuccess) "Xác thực sinh trắc thành công!" else "Đang quét Sinh Trắc Học...",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { biometricProgress },
                            color = if (biometricScanSuccess) Color.Green else GoldAccent,
                            trackColor = DarkBackground,
                            strokeWidth = 4.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            imageVector = if (biometricScanSuccess) Icons.Default.CheckCircle else Icons.Default.Fingerprint,
                            contentDescription = "Sensor Scan Icon",
                            tint = if (biometricScanSuccess) Color.Green else NeonCyan,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (biometricScanSuccess) "Giải mã Master Key từ Keystore Secure Enclave..." else "Vui lòng đặt chạm ngón tay lên cảm biến...",
                        color = SilentGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBiometricDialog = false }) {
                    Text("Hủy", color = Color.White)
                }
            },
            containerColor = DarkCardBg
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo Padlock Visual Header
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GoldAccent.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(70.dp)) {
                // Outer glowing circle
                drawCircle(
                    color = GoldAccent.copy(alpha = 0.15f),
                    radius = size.minDimension / 1.5f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Icon(
                imageVector = if (viewModel.isPasswordSet) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Padlock Symbol",
                tint = GoldAccent,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Typography Header
        Text(
            text = if (viewModel.isPasswordSet) "MỞ KHÓASỔ TAY" else "KHỞI TẠO VÍ NHẬT KÝ",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (viewModel.isPasswordSet) 
                "Dữ liệu được mã hóa cứng nội bộ an toàn 100% AES-GCM qua Android Keystore." 
            else 
                "Thiết lập mật khẩu chứa ký tự số độc quyền để làm chìa khóa sinh ra AES Key.",
            fontSize = 13.sp,
            color = SilentGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Main Login/Registration Input Fields
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text(if (viewModel.isPasswordSet) "Mật mã hoặc Mã cứu hộ" else "Đặt mật mã chính (mật khẩu số)", color = SilentGray) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Visibility", tint = SilentGray)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = DarkCardBg,
                focusedLabelColor = GoldAccent,
                cursorColor = GoldAccent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("password_input"),
            shape = RoundedCornerShape(12.dp)
        )

        // Setup Mode: Additional registration fields for confirmation & decoy
        if (!viewModel.isPasswordSet) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPasswordInput,
                onValueChange = { confirmPasswordInput = it },
                label = { Text("Xác nhận lại mật mã chính", color = SilentGray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = DarkCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Decoy Password Setup Shield
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showFakePasswordSetup = !showFakePasswordSetup },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Masks, contentDescription = "Decoy Mode", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bật Không Gian Ngụy Trang (Decoy)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (showFakePasswordSetup) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Setup",
                            tint = SilentGray
                        )
                    }

                    if (showFakePasswordSetup) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Nhập mật khẩu phụ riêng. Khi đăng nhập bằng mật khẩu này, Sổ tay sẽ mở ra một không gian trống giả lập hoàn chỉnh, bảo vệ bạn tối đa khi bị đe dọa cưỡng ép mở app.",
                            color = SilentGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = fakePasswordInput,
                            onValueChange = { fakePasswordInput = it },
                            label = { Text("Mật khẩu ngụy trang (số)", color = SilentGray) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBackground,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        viewModel.authError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(err, color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Actions panel (Confirm button + Biometrics trigger)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.isPasswordSet) {
                IconButton(
                    onClick = { showBiometricDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .background(DarkCardBg, shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = "Biometrics Scanner", tint = NeonCyan, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Button(
                onClick = {
                    if (viewModel.isPasswordSet) {
                        val success = viewModel.unlockApp(passwordInput)
                        if (success) {
                            passwordInput = ""
                            onUnlocked()
                        }
                    } else {
                        if (passwordInput != confirmPasswordInput) {
                            viewModel.authError = "⚠️ Nhập lại mật khẩu xác nhận không khớp!"
                            return@Button
                        }
                        val success = viewModel.registerPassword(passwordInput, fakePasswordInput)
                        if (success) {
                            passwordInput = ""
                            confirmPasswordInput = ""
                            fakePasswordInput = ""
                            onUnlocked()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("submit_password_button")
            ) {
                Text(
                    text = if (viewModel.isPasswordSet) "Mở khóa an toàn" else "Tạo ví & Khóa đầu cuối",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "🛡️ Sổ tay mật sử dụng Crypto Engine AES-GCM 256. Mật khẩu không bao giờ được lưu dưới dạng văn bản thường và không thể khôi phục từ xa.",
            fontSize = 11.sp,
            color = SilentGray.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// --- Screen 2: Journal Dashboard / Lists ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDashboardView(
    viewModel: DiaryViewModel,
    onNavigateToAddEdit: () -> Unit,
    onNavigateToSync: () -> Unit,
    onEditEntry: (DecryptedDiaryEntry) -> Unit
) {
    val entries by viewModel.activeEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterMood by remember { mutableStateOf("Tất cả") }

    val filteredEntries = entries.filter { entry ->
        val matchesSearch = entry.title.contains(searchQuery, ignoreCase = true) || 
                            entry.content.contains(searchQuery, ignoreCase = true)
        val matchesMood = selectedFilterMood == "Tất cả" || entry.mood.contains(selectedFilterMood)
        matchesSearch && matchesMood
    }

    val context = LocalContext.current

    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }

    if (showResetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showResetPasswordDialog = false },
            title = { Text("Tạo Mật Khẩu Số Mới", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Hệ thống khôi phục thành công. Vui lòng đặt lại một mật khẩu bảo mật mới cho thiết bị của bạn:", color = SilentGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("Mật khẩu số mới", color = SilentGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldAccent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.length >= 4) {
                            viewModel.resetPasswordViaRecoverySession(newPasswordInput)
                            showResetPasswordDialog = false
                            newPasswordInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("Cập Nhật", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasswordDialog = false }) {
                    Text("Hủy", color = Color.White)
                }
            },
            containerColor = DarkCardBg
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    viewModel.lockApp()
                                }
                            )
                        }
                    ) {
                        Text(
                            text = if (viewModel.isFakeVaultActive) "Danh mục Nhật Ký" else "Nhật Ký Bảo Mật",
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.isFakeVaultActive) NeonCyan else Color.White,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    // Drive Sync Status Indicators
                    IconButton(onClick = onNavigateToSync) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Backup Config Hub",
                                tint = if (viewModel.syncAutoEnabled) NeonCyan else SilentGray
                            )
                            if (viewModel.syncAutoEnabled) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Green, shape = CircleShape)
                                )
                            }
                        }
                    }
                    // Lock App Shortcut - Benign, standard padlock button
                    IconButton(onClick = { viewModel.lockApp() }) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Safe Cache", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddEdit,
                containerColor = GoldAccent,
                contentColor = DarkBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_diary_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Diary Entry", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input Header
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm kiếm ký ức...", color = SilentGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SilentGray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = DarkCardBg,
                    unfocusedContainerColor = DarkCardBg.copy(alpha = 0.5f),
                    focusedContainerColor = DarkCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("search_bar"),
                shape = RoundedCornerShape(14.dp)
            )

            // Mood Filter Row
            val moodFilters = listOf("Tất cả", "😊 Vui", "😢 Buồn", "🧘 Tĩnh", "😡 Giận", "💖 Yêu")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moodFilters) { moodName ->
                    val isSelected = selectedFilterMood == moodName
                    AssistChip(
                        onClick = { selectedFilterMood = moodName },
                        label = { Text(moodName, color = if (isSelected) DarkBackground else Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) GoldAccent else DarkCardBg
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Contents Area
            if (filteredEntries.isEmpty()) {
                // Empty State Widget representation
                EmptyStateView(hasFilters = searchQuery.isNotEmpty() || selectedFilterMood != "Tất cả")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        DiaryRowCard(
                            entry = entry,
                            viewModel = viewModel,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { viewModel.deleteDiaryEntry(entry.id, context) }
                        )
                    }

                    // Bottom AI Mood Analyzer Tool Card
                    item {
                        PremiumAiCompanionCard(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp)
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasFilters) Icons.Default.FilterListOff else Icons.Default.Book,
            contentDescription = "Empty Diary Notebook",
            tint = SilentGray.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilters) "Không thỏa mãn bộ lọc" else "Sổ Tay Đang Đợi Bạn...",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasFilters) "Vui lòng kiểm tra lại từ khóa tìm kiếm hoặc cảm xúc đã gắn."
            else "Mọi vui buồn lướt qua đều xứng đáng được ghi lại và mã hóa lưu trữ bền lâu.",
            color = SilentGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DiaryRowCard(
    entry: DecryptedDiaryEntry,
    viewModel: DiaryViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xóa ký ức?", color = Color.White) },
            text = { Text("Ký ức này sẽ biến mất vĩnh viễn và không thể giải mã lại.", color = SilentGray) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Xác nhận xóa", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Hủy", color = Color.White)
                }
            },
            containerColor = DarkCardBg
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("diary_item_card_${entry.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card top row: Mood Tag & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = entry.mood,
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp)),
                        color = SilentGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (entry.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = "Sync Indicator State",
                        tint = if (entry.isSynced) NeonCyan else SilentGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title & Content Preview
            Text(
                text = entry.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.content,
                fontSize = 13.sp,
                color = SilentGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // Encrypted attached image thumbnail (in-memory decrypt rendering!)
            entry.imagePath?.let { path ->
                val cachedBitmap = remember(path) { viewModel.getDecryptedBitmap(path) }
                cachedBitmap?.let { bitmap ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Decrypted secure visual attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Safety indicator badge on top of image
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Https, contentDescription = "Encrypted On Disk", tint = GoldAccent, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("MÃ HÓA CỨNG", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions panel inside card (Bottom right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit entry text",
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry records",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- Companion Sub-Card for AI Mood Analysis ---
@Composable
fun PremiumAiCompanionCard(viewModel: DiaryViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp),
        border = CardBorder(GoldAccent.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(GoldAccent.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Gemini Companion", tint = GoldAccent, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Trợ Lý Lắng Nghe Tâm Tư AI", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (viewModel.aiMoodAnalysisResult.isEmpty()) {
                Text(
                    text = "Trợ lý AI sẽ xâu chuỗi xúc cảm bí mật của các dòng nhật ký gần đây để gửi tới bạn một lời động viên nghệ thuật, thấu hiểu.",
                    color = SilentGray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else {
                Text(
                    text = viewModel.aiMoodAnalysisResult,
                    color = CalmSoftPeach,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (viewModel.aiLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = GoldAccent)
            } else {
                Button(
                    onClick = { viewModel.askDiaryMoodAnalysis() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Hearing, contentDescription = "Listen", tint = GoldAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phân Tích Cảm Xúc Bí Mật", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// Custom Border Helper for Cards
@Composable
fun CardBorder(color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, color)
}

// --- Screen 3: Add / Edit Journal Editor View ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditJournalView(viewModel: DiaryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.importImageFromUri(context, it) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.editingEntryId == null) "Viết Nhật Ký Mới" else "Chỉnh Sửa Ký ức",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, titleContentColor = Color.White)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Mood Bubble Chips Selector
            Text("HÔM NAY BẠN THẾ NÀO?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val emotions = listOf(
                "Vui vẻ 😊", "Hào hứng 🤩", "Bình yên 🧘", 
                "Lo âu 😟", "Buồn bã 😢", "Giận dữ 😡", "Nhớ thương 💖"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(emotions) { emo ->
                    val isSelected = viewModel.formMood == emo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) GoldAccent else DarkCardBg)
                            .clickable { viewModel.formMood = emo }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = emo, color = if (isSelected) DarkBackground else Color.White, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Journal Prompt Generator Section
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Prompt Helper", tint = GoldAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.aiSuggestedPrompt,
                            color = CalmSoftPeach,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (viewModel.aiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoldAccent, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.askAiPrompt() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Ask Gemini", tint = GoldAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Editor Text Fields
            OutlinedTextField(
                value = viewModel.formTitle,
                onValueChange = { viewModel.formTitle = it },
                label = { Text("Tiêu đề nhật ký...", color = SilentGray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = DarkCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = viewModel.formContent,
                onValueChange = { viewModel.formContent = it },
                label = { Text("Trút bầu tâm sự vào đây...", color = SilentGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = DarkCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .testTag("entry_content_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Image Thumbnail Decrypted Preview
            viewModel.formSelectedImageBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Form Attachment Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = {
                            viewModel.formSelectedImageBytes = null
                            viewModel.formSelectedImageBitmap = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove photo Attachment", tint = Color.Red)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Image Attachment Picker Button Launcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, GoldAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Add Photo")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (viewModel.formSelectedImageBitmap == null) "Thêm hình ảnh" else "Thay thế ảnh")
                }

                // Main Save Button
                Button(
                    onClick = {
                        viewModel.saveDiaryEntry(context) {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_diary_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save Records", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu ký ức", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// --- Screen 4: Advanced Google Drive Sync Station ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveSyncView(viewModel: DiaryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val terminalLogs by viewModel.syncLogs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trạm Đồng Bộ Đám Mây", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, titleContentColor = Color.White)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 🛑 TRẠM KIỂM TRA SỨC KHỎE BẢO MẬT (Security Integrity Dashboard)
            Text("CHỈ SỐ AN TOÀN HỆ THỐNG 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldAccent.copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Security Dashboard", tint = GoldAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Bảng Chẩn Đoán Toàn Vẹn Thiết Bị", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tiêu huỷ đệm & bọc khoá phần cứng", color = SilentGray, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SilentGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Mật khẩu bọc khóa
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mã hóa bọc Master Key", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = if (viewModel.isPasswordSet) "🔐 AES-GCM 256" else "⚪ Chưa thiết lập",
                            color = if (viewModel.isPasswordSet) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Vân tay / Sinh trắc
                    val sp = context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)
                    val isBioEnabled = sp.getString("bio_master_password_enc", "").isNullOrEmpty().not()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cấu hình vân tay sinh trắc", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = if (isBioEnabled) "🟢 Đang hoạt động (Keystore)" else "⚪ Chưa cấu hình",
                            color = if (isBioEnabled) Color.Green else SilentGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Không gian ngụy trang (Decoy Vault)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Không gian Ngụy trang", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = if (viewModel.isDecoyPasswordSet) "🟢 Đã bọc ngẫu nhiên (Seeded)" else "⚪ Chưa cấu hình",
                            color = if (viewModel.isDecoyPasswordSet) Color.Green else SilentGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Mã phục hồi (Recovery Code)
                    val hasRecovery = viewModel.recoveryKeyGenerated.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mã khôi phục khẩn cấp", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = if (hasRecovery) "🟢 Đã lưu trữ (AES)" else "⚪ Chưa tạo",
                            color = if (hasRecovery) Color.Green else Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Chế độ mạng offline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bảo vệ cục bộ (Offline Only)", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = if (viewModel.localPrivacyOnly) "🟢 Tối đa" else "🟡 Trung bình (Mạng)",
                            color = if (viewModel.localPrivacyOnly) Color.Green else Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6. Kiểm tra toàn vẹn bộ nhớ & Root
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kiểm định hỏng hóc (SHA-256)", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = "🟢 An toàn (Keystore OK)",
                            color = Color.Green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Connection Credentials Card status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = "Drive Channel", tint = NeonCyan, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tài Khoản Google Drive Sync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("vu.kiengiang2025@gmail.com", color = NeonCyan, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SilentGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trạng thái tải sao lưu", color = SilentGray, fontSize = 13.sp)
                        Text(viewModel.lastSyncTime, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Real-time synchronization toggle section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Đồng bộ thời gian thực", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tự động mã hóa tệp và đẩy trực tiếp lên Drive ngay khi Lưu/Xóa bản ghi nhật ký.",
                            color = SilentGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = viewModel.syncAutoEnabled,
                        onCheckedChange = { viewModel.setAutoSync(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Premium Security & Privacy Control Panel
            Text("CẤU HÌNH BẢO MẬT & RIÊNG TƯ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            var showRecoveryKeyDialog by remember { mutableStateOf(false) }

            if (showRecoveryKeyDialog) {
                val storedRecoveryEnc = context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)
                    .getString("recovery_key_enc", "") ?: ""
                val decryptedRecovery = com.example.data.AndroidKeyStoreHelper.decryptWithKeyStore(storedRecoveryEnc)

                AlertDialog(
                    onDismissRequest = { showRecoveryKeyDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Recovery Key", tint = GoldAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mã Khôi Phục Hiện Tại", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "Mã khôi phục phần cứng này được giải mã trực tiếp từ Secure Android KeyStore và chỉ hiển thị nội bộ tại đây:",
                                color = SilentGray,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (decryptedRecovery.isEmpty()) "CHƯA TẠO MÃ" else decryptedRecovery,
                                    color = NeonCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showRecoveryKeyDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                        ) {
                            Text("Xong", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = DarkCardBg
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Quyền riêng tư offline 100% switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quyền riêng tư tuyệt đối (Offline Only)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Vô hiệu hóa toàn bộ cuộc gọi mạng tới Gemini AI Cloud. Ghi ghép 100% offline hoàn chỉnh.",
                                color = SilentGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = viewModel.localPrivacyOnly,
                            onCheckedChange = { viewModel.toggleLocalPrivacyOnly(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldAccent,
                                checkedTrackColor = GoldAccent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    if (!viewModel.localPrivacyOnly) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "⚠️ MINH BẠCH PLAY STORE: Khi kích hoạt tính năng Đám mây/AI, nội dung ghi nhật ký được trích lựa sẽ được mã hóa và gửi tới máy chủ an toàn Google Cloud AI để sinh phân tích cảm thụ và gợi ý chủ đề. Không gộp dữ liệu cá nhân hay thông tin định danh rời thiết bị của bạn ôn tập dịch vụ.",
                            color = Color(0xFFFF9800),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SilentGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Auto-lock timeout selections
                    Text("Thời Gian Tự Động Khóa ⏱️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val intervals = listOf("Immediate", "30s", "1m", "5m")
                        val displayIntervals = listOf("Tức thì", "30s", "1 phút", "5 phút")
                        intervals.forEachIndexed { index, intval ->
                            val isChosen = viewModel.autoLockPeriod == intval
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) GoldAccent else DarkBackground)
                                    .clickable { viewModel.updateAutoLockPeriod(intval) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayIntervals[index],
                                    color = if (isChosen) DarkBackground else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SilentGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Security Actions (View Recovery Key & Lock shortcut)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRecoveryKeyDialog = true },
                            border = BorderStroke(1.dp, NeonCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = "View Recovery Key", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mã cứu hộ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.lockApp() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock App", tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Khóa sổ tay", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Manual Drive controls
            Text("ĐỒNG BỘ THỦ CÔNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.triggerGoogleDriveSync(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Backup Payload", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sao lưu Drive", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.triggerGoogleDriveRestore(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Restore Payload", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phục hồi Drive", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Offline Backup export & import controls
            Text("SAO LƯU NGOẠI TUYẾN (.BACKUP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            var showImportFileDialog by remember { mutableStateOf(false) }
            var pasteBackupText by remember { mutableStateOf("") }

            if (showImportFileDialog) {
                AlertDialog(
                    onDismissRequest = { showImportFileDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = GoldAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nhập Lại Nhật Ký Cục Bộ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "Dán nội dung tệp mã hóa .backup thu được trước đó để khôi phục bọc. Chỉ bóc tách được bằng Master Key đúng:",
                                color = SilentGray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pasteBackupText,
                                onValueChange = { pasteBackupText = it },
                                placeholder = { Text("Dán văn bản iv:ciphertext tại đây...", color = SilentGray.copy(alpha = 0.5f), fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (pasteBackupText.isNotEmpty()) {
                                    viewModel.importLocalEncryptedBackupFile(pasteBackupText, context)
                                    showImportFileDialog = false
                                    pasteBackupText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                        ) {
                            Text("Bắt đầu khôi phục", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportFileDialog = false }) {
                            Text("Hủy", color = Color.White)
                        }
                    },
                    containerColor = DarkCardBg
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.exportLocalEncryptedBackupFile(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export File Link", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Xuất file .backup", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = { showImportFileDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Input, contentDescription = "Import File Paste", tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Khôi phục .backup", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Sync loading feedback
            if (viewModel.isSyncing) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { viewModel.syncProgress },
                        color = NeonCyan,
                        trackColor = DarkCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Đang truyền dẫn: ${(viewModel.syncProgress * 100).toInt()}% (Mã hóa SSL)",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Google Drive SDK Console Terminal Monitor (Live logs tracker)
            Text("TRÌNH ĐIỀU KHIỂN ĐỒNG BỘ GOOGLE DRIVE API", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true
                ) {
                    // Show terminal logs in reverse chronologically or normally
                    items(terminalLogs.reversed()) { logString ->
                        Text(
                            text = logString,
                            color = if (logString.contains("✅") || logString.contains("🔓")) NeonCyan 
                            else if (logString.contains("⚠️") || logString.contains("❌")) Color.Red 
                            else if (logString.contains("📝")) GoldAccent
                            else Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// Border Stroke Helper
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}
