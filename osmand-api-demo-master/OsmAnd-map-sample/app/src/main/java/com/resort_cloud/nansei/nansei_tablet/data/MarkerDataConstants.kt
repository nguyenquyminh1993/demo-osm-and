package com.resort_cloud.nansei.nansei_tablet.data

/**
 * Marker data constants extracted from miyako_map.html
 * Contains all marker information: name, coordinates, and facility kind
 */
object MarkerDataConstants {
    
    /**
     * Marker data structure
     */
    data class MarkerInfo(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val facilityKind: String
    )
    
    /**
     * Hotel markers (hotelplace from HTML)
     */
    val HOTEL_MARKERS = listOf(
        MarkerInfo("ロイヤル（アラマンダ）", 24.7233, 125.3338, "hotel"),
        MarkerInfo("ラグーン館（アラマンダ）", 24.72388, 125.33466, "hotel"),
        MarkerInfo("本館（アラマンダ）", 24.72320, 125.33637, "hotel"),
        MarkerInfo("ジャグジー館（アラマンダ）", 24.72340, 125.33803, "hotel"),
        MarkerInfo("ヴィラ", 24.72253, 125.33734, "hotel"),
        MarkerInfo("プールヴィラ（ミラージュ）", 24.72289, 125.34025, "hotel"),
        MarkerInfo("ベイサイド（ミラージュ）", 24.72103, 125.33926, "hotel"),
        MarkerInfo("シーブリーズコーラル", 24.72071, 125.33303, "hotel"),
        MarkerInfo("ホットクロスポイント\nサンタモニカ", 24.72000, 125.33260, "hotel"),
        MarkerInfo("ベイフロント（ザ シギラ）", 24.71842, 125.33972, "hotel"),
        MarkerInfo("レセプション", 24.71931, 125.33860, "hotel"),
        MarkerInfo("ザ シギラ\nラ・ブルーヴィスタ", 24.72422, 125.34912, "hotel"),
        MarkerInfo("ビーチフロント（ミラージュ）", 24.71982, 125.33987, "hotel")
    )
    
    /**
     * Restaurant markers (restaurantplace from HTML)
     */
    val RESTAURANT_MARKERS = listOf(
        MarkerInfo("HORIZON", 24.72087, 125.34171, "restaurant"),
        MarkerInfo("フードコート", 24.72081, 125.34143, "restaurant"),
        MarkerInfo("シギラ\nタートルベイ", 24.72091, 125.34054, "restaurant"),
        MarkerInfo("スターダストガーデン", 24.72020, 125.33843, "restaurant"),
        MarkerInfo("彩海", 24.72042, 125.33769, "restaurant"),
        MarkerInfo("琉宮苑", 24.72075, 125.33427, "restaurant"),
        MarkerInfo("小肥羊", 24.72068, 125.33398, "restaurant"),
        MarkerInfo("ファンキーフラミンゴ", 24.72001, 125.33391, "restaurant"),
        MarkerInfo("カフェ", 24.71997, 125.33508, "restaurant"),
        MarkerInfo("レストラン", 24.72193, 125.34490, "restaurant"),
        MarkerInfo("グランマーレ", 24.72387, 125.33530, "restaurant"),
        MarkerInfo("マラルンガ", 24.72333, 125.33625, "restaurant"),
        MarkerInfo("マイヨール", 24.72338, 125.33653, "restaurant"),
        MarkerInfo("ラウンジ", 24.72110, 125.33945, "restaurant"),
        MarkerInfo("蜃気楼", 24.72125, 125.33910, "restaurant")
    )
    
    /**
     * Facility markers (facilityplace from HTML)
     */
    val FACILITY_MARKERS = listOf(
        MarkerInfo("レンタカー", 24.71951, 125.33684, "facility"),
        MarkerInfo("ビーチハウス", 24.72081, 125.34111, "facility"),
        MarkerInfo("フィールドハウス", 24.71962, 125.33670, "facility"),
        MarkerInfo("温泉", 24.71972, 125.33493, "facility"),
        MarkerInfo("ATM", 24.72020, 125.33278, "facility")
    )
    
    /**
     * Shop markers (shopplace from HTML)
     */
    val SHOP_MARKERS = listOf(
        MarkerInfo("ショップ", 24.72031, 125.33419, "shop"),
        MarkerInfo("シギラショップ", 24.72098, 125.34150, "shop"),
        MarkerInfo("ショップ", 24.72087, 125.33926, "shop"),
        MarkerInfo("ショップ", 24.72328, 125.33653, "shop")
    )
    
    /**
     * Beach markers (beachplace from HTML)
     */
    val BEACH_MARKERS = listOf(
        MarkerInfo("シギラビーチ", 24.72023, 125.34083, "beach")
    )
    
    /**
     * Lift markers (liftplace from HTML)
     */
    val LIFT_MARKERS = listOf(
        MarkerInfo("リフト", 24.72113, 125.34194, "lift")
    )
    
    /**
     * Golf markers (golfplace from HTML)
     */
    val GOLF_MARKERS = listOf(
        MarkerInfo("シギラベイＣＣ", 24.72202, 125.34499, "golf")
    )
    
    /**
     * Parking markers (parkingplace from HTML)
     */
    val PARKING_MARKERS = listOf(
        MarkerInfo("シギラベイＣＣ", 24.72230, 125.34463, "parking"),
        MarkerInfo("シギラビーチ", 24.7211, 125.34148, "parking"),
        MarkerInfo("タートルベイ（下）", 24.72075, 125.34044, "parking"),
        MarkerInfo("タートルベイ（上）", 24.72089, 125.34022, "parking"),
        MarkerInfo("ミラージュ", 24.72064, 125.33957, "parking"),
        MarkerInfo("アラマンダジャグジー", 24.72361, 125.33805, "parking"),
        MarkerInfo("アラマンダ", 24.72344, 125.33605, "parking"),
        MarkerInfo("スターダスト", 24.72044, 125.33830, "parking"),
        MarkerInfo("彩海", 24.72038, 125.33758, "parking"),
        MarkerInfo("レンタカー", 24.71963, 125.33684, "parking"),
        MarkerInfo("温泉", 24.72017, 125.33519, "parking"),
        MarkerInfo("小肥羊", 24.72089, 125.33382, "parking"),
        MarkerInfo("サンタモニカ", 24.72048, 125.33436, "parking"),
        MarkerInfo("ビーチフロント上部", 24.72005, 125.33965, "parking"),
        MarkerInfo("ビーチフロント下部", 24.71924, 125.33986, "parking")
    )
    
    /**
     * Chapel markers (chapelplace from HTML)
     */
    val CHAPEL_MARKERS = listOf(
        MarkerInfo("チャペル", 24.72236, 125.33660, "chapel"),
        MarkerInfo("チャペル", 24.72038, 125.33949, "chapel")
    )

    val ARROW_MARKER = listOf(
        MarkerInfo("", 24.72053, 125.33959, "arrow"),
    )

    val BLOCK_MARKER = listOf(
        MarkerInfo("", 24.72001, 125.33934, "blocked"),
        MarkerInfo("", 24.71963, 125.33922, "blocked"),
    )
    /**
     * Get all markers combined
     */
    fun getAllMarkers(): List<MarkerInfo> {
        return HOTEL_MARKERS +
                RESTAURANT_MARKERS +
                FACILITY_MARKERS +
                SHOP_MARKERS +
                BEACH_MARKERS +
                LIFT_MARKERS +
                GOLF_MARKERS +
                PARKING_MARKERS +
                CHAPEL_MARKERS +
                ARROW_MARKER +
                BLOCK_MARKER
    }
}



