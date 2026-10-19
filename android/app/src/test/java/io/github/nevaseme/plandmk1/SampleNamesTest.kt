package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.ui.samplePersonName
import io.github.nevaseme.plandmk1.ui.displayLabel
import io.github.nevaseme.plandmk1.core.PlaceEvent
import io.github.nevaseme.plandmk1.core.PlaceKind
import org.junit.Test
import org.junit.Assert.*

class SampleNamesTest {
    @Test fun onlyKnownLabNamesReceiveAliases() {
        assertEquals("山田 太郎", samplePersonName("lab-1007", "Drill-A"))
        assertEquals("山田 太郎", samplePersonName("lab-1007", "drill-a"))
        assertEquals("佐藤 花子", samplePersonName("lab-1007", "訓練B"))
        assertEquals("鈴木 一郎（確認担当）", samplePersonName("lab-1007", "訓練C・確認担当"))
        assertEquals("Drill-A", samplePersonName("other", "Drill-A"))
        assertEquals("自由な登録名", samplePersonName("lab-1007", "自由な登録名"))
    }
    @Test fun buildingAliasDoesNotRewriteRecord() {
        val original=PlaceEvent("lab-1007","event","author","Drill-A",1,1,PlaceKind.BUILDING,buildingId="house",label="Drill-House",latitude=35.0,longitude=139.0)
        assertEquals("さくらハイツ", original.displayLabel())
        assertEquals("Drill-House", original.label)
        assertEquals("Drill-House", original.copy(groupId="other").displayLabel())
        assertEquals("自宅", original.copy(label="自宅").displayLabel())
    }
}
