package com.nutriscan.app.ui.manual

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutriscan.app.R
import com.nutriscan.app.ui.rememberAppContainer
import com.nutriscan.app.ui.theme.MonospaceStyle
import com.nutriscan.app.util.PackagePhotoStore
import com.nutriscan.app.util.decodeSampledBitmap
import java.io.File

/**
 * Unknown-product recovery: photograph the package and type the label values in.
 *
 * Nothing here is published anywhere. The draft is stored on device and merged into the product
 * screen as "user entered" data for fields no database provides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    barcode: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val viewModel: ManualEntryViewModel = viewModel(
        key = "manual-$barcode",
        factory = viewModelFactory {
            initializer { ManualEntryViewModel(barcode, container.userProductRepository) }
        }
    )
    val form by viewModel.form.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var pendingFile by remember { mutableStateOf<File?>(null) }
    var photoError by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null && file.length() > 0) {
            viewModel.setPhoto(file.absolutePath)
        } else {
            file?.delete()
        }
        pendingFile = null
    }

    // The app declares CAMERA for scanning. Android 11+ refuses to start an IMAGE_CAPTURE intent
    // from an app that declares CAMERA without holding it, so the grant is checked first and the
    // launch itself is guarded: a refused or missing camera app must not take the app down.
    val launchCamera: () -> Unit = {
        val file = PackagePhotoStore.newPhotoFile(context, barcode)
        pendingFile = file
        photoError = false
        runCatching { cameraLauncher.launch(PackagePhotoStore.uriFor(context, file)) }
            .onFailure {
                file.delete()
                pendingFile = null
                photoError = true
            }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else photoError = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.manual_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.details_barcode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = barcode, style = MonospaceStyle)
                    }
                }
            }

            item {
                PhotoSection(
                    photoPath = form.photoPath,
                    photoError = photoError,
                    onTakePhoto = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onRemovePhoto = { viewModel.setPhoto(null) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.manual_read_from_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.manual_read_from_label_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = form.readFromLabel,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(readFromLabel = checked) }
                        }
                    )
                }
            }

            item {
                FormField(
                    label = stringResource(R.string.manual_name),
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } }
                )
            }
            item {
                FormField(
                    label = stringResource(R.string.manual_brand),
                    value = form.brand,
                    onValueChange = { v -> viewModel.update { it.copy(brand = v) } }
                )
            }
            item {
                FormField(
                    label = stringResource(R.string.manual_quantity),
                    value = form.quantity,
                    onValueChange = { v -> viewModel.update { it.copy(quantity = v) } }
                )
            }
            item {
                FormField(
                    label = stringResource(R.string.manual_serving_size),
                    value = form.servingSize,
                    onValueChange = { v -> viewModel.update { it.copy(servingSize = v) } }
                )
            }
            item {
                FormField(
                    label = stringResource(R.string.manual_ingredients),
                    value = form.ingredients,
                    singleLine = false,
                    onValueChange = { v -> viewModel.update { it.copy(ingredients = v) } }
                )
            }
            item {
                FormField(
                    label = stringResource(R.string.manual_allergens),
                    value = form.allergens,
                    supporting = stringResource(R.string.manual_allergens_hint),
                    onValueChange = { v -> viewModel.update { it.copy(allergens = v) } }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.manual_nutrition_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.nutrition_per_100g),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                NumberField(stringResource(R.string.nutrient_energy), form.energyKcal) { v ->
                    viewModel.update { it.copy(energyKcal = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_fat), form.fat) { v ->
                    viewModel.update { it.copy(fat = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_saturated_fat), form.saturatedFat) { v ->
                    viewModel.update { it.copy(saturatedFat = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_carbohydrates), form.carbohydrates) { v ->
                    viewModel.update { it.copy(carbohydrates = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_sugars), form.sugars) { v ->
                    viewModel.update { it.copy(sugars = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_added_sugars), form.addedSugars) { v ->
                    viewModel.update { it.copy(addedSugars = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_protein), form.protein) { v ->
                    viewModel.update { it.copy(protein = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_salt), form.salt) { v ->
                    viewModel.update { it.copy(salt = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_sodium), form.sodium) { v ->
                    viewModel.update { it.copy(sodium = v) }
                }
            }
            item {
                NumberField(stringResource(R.string.nutrient_fiber), form.fiber) { v ->
                    viewModel.update { it.copy(fiber = v) }
                }
            }

            error?.let { current ->
                item {
                    Text(
                        text = stringResource(
                            when (current) {
                                ManualEntryError.EMPTY -> R.string.manual_error_empty
                                ManualEntryError.INVALID_NUMBER -> R.string.manual_error_invalid_number
                                ManualEntryError.SAVE_FAILED -> R.string.manual_error_save
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Button(
                    onClick = { if (viewModel.save()) onSaved() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.manual_save))
                }
            }

            if (viewModel.hasExistingEntry) {
                item {
                    TextButton(
                        onClick = {
                            viewModel.deleteEntry()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.manual_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.manual_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoPath: String?,
    photoError: Boolean,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.manual_photo_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.manual_photo_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            val bitmap = remember(photoPath) {
                photoPath?.let { decodeSampledBitmap(it, 720) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.manual_photo_title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (bitmap == null) R.string.manual_take_photo else R.string.manual_retake_photo
                        )
                    )
                }
                if (bitmap != null) {
                    OutlinedButton(onClick = onRemovePhoto, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.manual_remove_photo))
                    }
                }
            }

            if (photoError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.manual_photo_error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() || it == '.' || it == ',' })
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
