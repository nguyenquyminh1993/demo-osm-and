package com.resort_cloud.nansei.nansei_tablet.utils

import com.resort_cloud.nansei.nansei_tablet.data.model.Facility
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityKind

/**
 * Helper class để filter facilities dựa trên search term
 * Logic tương tự Flutter: search by name và nameKana, hỗ trợ hiragana/katakana
 */
object SearchHelper {
    
    /**
     * Filter facilities dựa trên search term
     * @param facilityKinds Danh sách facility kinds
     * @param searchTerm Từ khóa tìm kiếm
     * @return Danh sách FacilityItem (có thể là category title hoặc facility)
     */
    fun filterFacilities(
        facilityKinds: List<FacilityKind>,
        searchTerm: String
    ): List<FacilityItem> {
        val result = mutableListOf<FacilityItem>()
        
        // Nếu search term rỗng, trả về tất cả
        val trimmedSearchTerm = searchTerm.trim()
        if (trimmedSearchTerm.isEmpty()) {
            // Trả về tất cả facilities với category titles
            facilityKinds.forEach { kind ->
                result.add(FacilityItem.createCategoryTitle(kind.name))
                kind.facilities.forEach { facility ->
                    if (facility.listData.visible) {
                        result.add(FacilityItem.createFacility(facility, kind.name))
                    }
                }
            }
            return result
        }
        
        // Filter theo search term
        facilityKinds.forEach { kind ->
            val matchingFacilities = kind.facilities.filter { facility ->
                if (!facility.listData.visible) {
                    return@filter false
                }
                
                // Kiểm tra name hoặc nameKana có chứa search term không
                containsText(facility.listData.name, trimmedSearchTerm) ||
                containsText(facility.listData.nameKana, trimmedSearchTerm)
            }
            
            // Nếu có facilities match, thêm category title và facilities
            if (matchingFacilities.isNotEmpty()) {
                result.add(FacilityItem.createCategoryTitle(kind.name))
                matchingFacilities.forEach { facility ->
                    result.add(FacilityItem.createFacility(facility, kind.name))
                }
            }
        }
        
        return result
    }
    
    /**
     * Kiểm tra text có chứa search term không
     * Hỗ trợ hiragana/katakana conversion
     */
    private fun containsText(text: String, searchTerm: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        
        // Loại bỏ khoảng trắng
        val cleanText = text.replace(" ", "").replace("　", "")
        val cleanSearchTerm = searchTerm.replace(" ", "").replace("　", "")
        
        // Kiểm tra contains trực tiếp
        if (cleanText.contains(cleanSearchTerm, ignoreCase = true)) {
            return true
        }
        
        // Chuyển hiragana sang katakana và kiểm tra lại
        val katakanaText = hiraganaToKatakana(cleanText)
        val katakanaSearchTerm = hiraganaToKatakana(cleanSearchTerm)
        if (katakanaText.contains(katakanaSearchTerm, ignoreCase = true)) {
            return true
        }
        
        return false
    }
    
    /**
     * Chuyển đổi hiragana sang katakana
     * Logic tương tự Flutter: replaceAllMapped(RegExp("[ぁ-ゔ]"), ...)
     */
    private fun hiraganaToKatakana(text: String): String {
        return text.map { char ->
            // Hiragana range: ぁ (0x3041) to ゔ (0x3094)
            // Katakana range: ァ (0x30A1) to ヴ (0x30F4)
            // Difference: 0x60
            if (char.code in 0x3041..0x3094) {
                Char(char.code + 0x60)
            } else {
                char
            }
        }.joinToString("")
    }
}

/**
 * Item trong search list - có thể là category title hoặc facility
 */
sealed class FacilityItem {
    abstract val isTitle: Boolean
    abstract val name: String
    
    data class CategoryTitle(
        override val name: String
    ) : FacilityItem() {
        override val isTitle: Boolean = true
    }
    
    data class FacilityData(
        val facility: Facility,
        val categoryName: String
    ) : FacilityItem() {
        override val isTitle: Boolean = false
        override val name: String = facility.listData.name
        val nameKana: String = facility.listData.nameKana
        val description: String = facility.markerData.description
        val facilityId: Int = facility.facilityId
        val latitude: Double = facility.markerData.latitude
        val longitude: Double = facility.markerData.longitude
    }
    
    companion object {
        fun createCategoryTitle(name: String): FacilityItem = CategoryTitle(name)
        fun createFacility(facility: Facility, categoryName: String): FacilityItem = 
            FacilityData(facility, categoryName)
    }
}

