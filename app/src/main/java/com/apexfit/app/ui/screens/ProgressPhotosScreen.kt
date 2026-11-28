package com.apexfit.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apexfit.app.data.PhotoPose
import com.apexfit.app.data.ProgressPhoto
import com.apexfit.app.data.ProgressPhotoStore
import com.apexfit.app.ui.theme.ApexColors
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProgressPhotosScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ProgressPhotoStore(context) }
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<ProgressPhoto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pose by rememberSaveable { mutableStateOf(PhotoPose.OTHER) }
    var pendingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPose by rememberSaveable { mutableStateOf(PhotoPose.OTHER) }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var second by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ProgressPhoto?>(null) }
    LaunchedEffect(reload) {
        try {
            photos = store.list()
            val ids = photos.orEmpty().filter { it.available }.map { it.id }.toSet()
            if (first !in ids) first = null
            if (second !in ids) second = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load private photos. Retry." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val selectedDate = pendingDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val selectedPose = pendingPose
        pendingDate = null
        if (uri != null && selectedDate != null) {
            busy = true
            error = null
            scope.launch {
                try { store.add(uri, selectedDate, selectedPose); reload++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Photo could not be saved. Choose a supported image under 32 MB."; reload++ }
                finally { busy = false }
            }
        }
    }
    if (adding) {
        val parsed = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add progress photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("A resized copy will be stored privately on this device. It is not included in backup or CSV export.")
                    OutlinedTextField(date, { if (it.length <= 10) date = it }, label = { Text("Photo date YYYY-MM-DD") },
                        singleLine = true)
                    PhotoPose.entries.forEach { choice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = pose == choice, onClick = { pose = choice })
                            Text(choice.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }, confirmButton = {
                TextButton(enabled = parsed != null && parsed <= LocalDate.now() && !busy, onClick = {
                    pendingDate = parsed.toString()
                    pendingPose = pose
                    adding = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose photo") }
            }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
    }
    deleting?.let { photo ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Delete private copy?") },
            text = { Text("The photo and thumbnail stored in Apex Fit will be removed. The original in your gallery is not affected.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { store.delete(photo); deleting = null; reload++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Deletion could not be completed. Retry."; deleting = null; reload++ }
                    finally { busy = false }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") } })
    }
    val list = photos
    val a = list?.firstOrNull { it.id == first && it.available }
    val b = list?.firstOrNull { it.id == second && it.available }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack, enabled = !busy) { Text("Back to Progress") }
            Text("Progress photos", style = MaterialTheme.typography.headlineMedium)
            Text("Private to this installation. Photos are not uploaded or analyzed.", color = ApexColors.TextSecondary)
            TextButton(enabled = !busy, onClick = { date = LocalDate.now().toString(); adding = true }) { Text("Add photo") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { error = null; reload++ }) { Text("Retry") } }
        }
        if (a != null && b != null) item {
            Text("Compare photos", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(a,b).forEach { photo ->
                    Column(Modifier.weight(1f)) {
                        PrivatePhotoImage(store, photo, false, Modifier.fillMaxWidth().height(240.dp))
                        Text("${photo.date} · ${photo.pose.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Lighting, pose, distance and clothing affect appearance. No body-fat or physique score is inferred.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { first = null; second = null }) { Text("Clear comparison") }
        }
        if (list == null && error == null) item { CircularProgressIndicator() }
        if (list?.isEmpty() == true) item { Text("No progress photos yet.", color = ApexColors.TextSecondary) }
        items(list.orEmpty(), key = { it.id }) { photo ->
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivatePhotoImage(store, photo, true, Modifier.size(82.dp, 105.dp))
                Column(Modifier.weight(1f)) {
                    Text(photo.date.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(photo.pose.name.lowercase(), color = ApexColors.TextSecondary)
                    if (!photo.available) Text("Photo files missing. Delete this entry.", color = ApexColors.TextSecondary)
                    else TextButton(enabled = !busy, onClick = {
                        when (photo.id) {
                            first -> { first = second; second = null }
                            second -> second = null
                            else -> if (first == null) first = photo.id else second = photo.id
                        }
                    }) { Text(if (photo.id == first || photo.id == second) "Selected for comparison" else "Select to compare") }
                    TextButton(enabled = !busy, onClick = { deleting = photo }) { Text("Delete photo") }
                }
            }
        }
    }
}

@Composable
private fun PrivatePhotoImage(store: ProgressPhotoStore, photo: ProgressPhoto, thumbnail: Boolean, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, photo, thumbnail) {
        if (photo.available) value = try { store.bitmap(photo, thumbnail) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    val image = bitmap
    Box(modifier.background(ApexColors.BorderSubtle), contentAlignment = Alignment.Center) {
        if (image != null) Image(image.asImageBitmap(), contentDescription = "Progress photo ${photo.date} ${photo.pose.name.lowercase()}",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(if (photo.available) "Photo unavailable" else "Missing", style = MaterialTheme.typography.labelSmall)
    }
}
