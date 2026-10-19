package io.github.nevaseme.plandmk1.nearby

data class SharingFailure(val message: String, val showPlayServicesSettings: Boolean = false)

fun sharingFailure(statusCode: Int?, ownAdvertiseGranted: Boolean, playServicesAdvertiseGranted: Boolean?, detail: String): SharingFailure {
    if (statusCode == 8038 && ownAdvertiseGranted && playServicesAdvertiseGranted == false) {
        return SharingFailure(
            "Google Play開発者サービスのBluetooth権限が不足しています。下のボタンから権限設定を開き、「付近のデバイス」を許可してから共有を開始し直してください。",
            showPlayServicesSettings = true,
        )
    }
    return SharingFailure("共有を開始できません。権限・Bluetooth・Wi-Fi・位置情報設定を確認して再試行してください。$detail")
}
