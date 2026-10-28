package com.nutriscan.app.ui.manual

import androidx.lifecycle.ViewModel
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.UserProductDraft
import com.nutriscan.app.repository.UserProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the recovery form holds, as raw strings so a partially typed form is never lost. */
data class ManualEntryForm(
    val barcode: String,
    val name: String = "",
    val brand: String = "",
    val quantity: String = "",
    val servingSize: String = "",
    val ingredients: String = "",
    val allergens: String = "",
    val energyKcal: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val carbohydrates: String = "",
    val sugars: String = "",
    val addedSugars: String = "",
    val protein: String = "",
    val salt: String = "",
    val sodium: String = "",
    val fiber: String = "",
    val readFromLabel: Boolean = true,
    val photoPath: String? = null
) {
    val isEmpty: Boolean
        get() = name.isBlank() && brand.isBlank() && ingredients.isBlank() &&
            allergens.isBlank() && numericFields().all { it.second.isBlank() }

    fun numericFields(): List<Pair<NutrientKind, String>> = listOf(
        NutrientKind.ENERGY_KCAL to energyKcal,
        NutrientKind.FAT to fat,
        NutrientKind.SATURATED_FAT to saturatedFat,
        NutrientKind.CARBOHYDRATES to carbohydrates,
        NutrientKind.SUGARS to sugars,
        NutrientKind.ADDED_SUGARS to addedSugars,
        NutrientKind.PROTEIN to protein,
        NutrientKind.SALT to salt,
        NutrientKind.SODIUM to sodium,
        NutrientKind.FIBER to fiber
    )
}

enum class ManualEntryError { EMPTY, INVALID_NUMBER, SAVE_FAILED }

/**
 * Backs the unknown-product recovery form. Values are stored locally only; nothing is ever sent to
 * a public database from this screen.
 */
class ManualEntryViewModel(
    private val barcode: String,
    private val userProductRepository: UserProductRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _form = MutableStateFlow(initialForm())
    val form: StateFlow<ManualEntryForm> = _form.asStateFlow()

    private val _error = MutableStateFlow<ManualEntryError?>(null)
    val error: StateFlow<ManualEntryError?> = _error.asStateFlow()

    val hasExistingEntry: Boolean = userProductRepository.find(barcode) != null

    fun update(transform: (ManualEntryForm) -> ManualEntryForm) {
        _form.value = transform(_form.value)
        _error.value = null
    }

    fun setPhoto(path: String?) {
        _form.value = _form.value.copy(photoPath = path)
    }

    /** Returns true when the draft was written to local storage. */
    fun save(): Boolean {
        val form = _form.value
        if (form.isEmpty) {
            _error.value = ManualEntryError.EMPTY
            return false
        }

        val parsed = mutableListOf<Nutrient>()
        for ((kind, raw) in form.numericFields()) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            val value = text.replace(',', '.').toDoubleOrNull()
            if (value == null || !value.isFinite() || value < 0) {
                _error.value = ManualEntryError.INVALID_NUMBER
                return false
            }
            parsed += Nutrient(kind, value, unitFor(kind))
        }

        val draft = UserProductDraft(
            barcode = barcode,
            name = form.name.trim().ifEmpty { null },
            brand = form.brand.trim().ifEmpty { null },
            quantity = form.quantity.trim().ifEmpty { null },
            servingSize = form.servingSize.trim().ifEmpty { null },
            ingredients = form.ingredients.trim().ifEmpty { null },
            allergens = form.allergens.split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            nutrients = parsed,
            basis = NutritionBasis.PER_100_G,
            readFromLabel = form.readFromLabel,
            photoPath = form.photoPath,
            updatedAt = clock()
        )

        return runCatching {
            userProductRepository.save(draft)
            true
        }.getOrElse {
            _error.value = ManualEntryError.SAVE_FAILED
            false
        }
    }

    fun deleteEntry(): Boolean {
        userProductRepository.delete(barcode)
        _form.value = initialForm()
        return true
    }

    private fun initialForm(): ManualEntryForm {
        val existing = userProductRepository.find(barcode) ?: return ManualEntryForm(barcode = barcode)
        fun value(kind: NutrientKind): String =
            existing.nutrients.firstOrNull { it.kind == kind }?.amount?.let(::formatForInput).orEmpty()

        return ManualEntryForm(
            barcode = barcode,
            name = existing.name.orEmpty(),
            brand = existing.brand.orEmpty(),
            quantity = existing.quantity.orEmpty(),
            servingSize = existing.servingSize.orEmpty(),
            ingredients = existing.ingredients.orEmpty(),
            allergens = existing.allergens.joinToString(", "),
            energyKcal = value(NutrientKind.ENERGY_KCAL),
            fat = value(NutrientKind.FAT),
            saturatedFat = value(NutrientKind.SATURATED_FAT),
            carbohydrates = value(NutrientKind.CARBOHYDRATES),
            sugars = value(NutrientKind.SUGARS),
            addedSugars = value(NutrientKind.ADDED_SUGARS),
            protein = value(NutrientKind.PROTEIN),
            salt = value(NutrientKind.SALT),
            sodium = value(NutrientKind.SODIUM),
            fiber = value(NutrientKind.FIBER),
            readFromLabel = existing.readFromLabel,
            photoPath = existing.photoPath
        )
    }

    private fun formatForInput(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun unitFor(kind: NutrientKind): String = when (kind) {
        NutrientKind.ENERGY_KCAL -> "kcal"
        else -> "g"
    }
}
