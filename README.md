# FCM Guard ColorOS

FCM Guard ColorOS là bản chuyển đổi của FCM Guard theo hướng **ColorOS/OPlus**, tập trung vào độ ổn định của Google Play Services / FCM trên máy China ROM mà **không cần Root và không cần Shizuku**.

## Mục tiêu v0.1

- Giữ watchdog của FCM Guard chạy bằng foreground service do người dùng bật.
- Gửi `GTALK_HEARTBEAT` và `MCS_HEARTBEAT` đến Google Play Services / Google Services Framework để yêu cầu reconnect best-effort.
- Reconnect khi:
  - bật bảo vệ;
  - khởi động máy;
  - mạng có lại hoặc đổi mạng;
  - mở khóa sau thời gian sleep dài;
  - fallback 30 phút khi watchdog vẫn còn sống;
  - người dùng bấm **Reconnect FCM ngay**.
- Không báo giả `FCM OK`: app thường không có API công khai để xác minh socket FCM thực sự đang connected.
- Mở nhanh các trang ColorOS/OPlus:
  - Auto launch;
  - Associated launch;
  - Battery / Power manager.
- Quét best-effort các app có manifest signal của FCM/GCM để hỗ trợ kiểm tra Gmail, app ngân hàng và các app nhận push khác.

## Thiết kế quyền

Bản ColorOS **không dùng** `WRITE_SETTINGS`, không dùng private Xiaomi setting `MILLET_NO_RESTRICT_APP`, không dùng Xiaomi AppOps và không phụ thuộc `com.miui.securitycenter`.

Quyền chính:

- `RECEIVE_BOOT_COMPLETED`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`

Ứng dụng dùng `targetSdk 35` và một application id riêng:

```text
com.hoanggleee.fcmguardcoloros
```

vì vậy có thể cài song song với bản FCMGuard-HyperOS để A/B test.

## ColorOS đã xác định trên thiết bị thử nghiệm

Các entry point OPlus đang được thử theo best-effort:

```text
com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity
com.oplus.battery/com.oplus.startupapp.view.AssociateStartActivity
com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerConsumptionActivity
```

Nếu ROM không cho app thường mở component cụ thể, ứng dụng sẽ thử action tương ứng hoặc fallback sang trang Settings phù hợp.

## Giới hạn

FCM Guard ColorOS là app thường. Nó không thể ép `com.google.android.gms` thành tiến trình bất tử, không thể chặn Athena bằng quyền hệ thống, và không thể tự thay đổi các policy signature-only của OPlus.

Heartbeat là **best-effort reconnect request**, không phải bằng chứng rằng socket FCM đã reconnect thành công.

## Build

GitHub Actions dùng JDK 17, Android SDK 35 và build:

```text
gradle :app:assembleDebug --stacktrace
```

Nhánh phát triển hiện tại:

```text
coloros-core-v1
```

## Nguồn gốc

Project bắt đầu từ mã nguồn FCMGuard-HyperOS và giữ lại cơ chế reconnect FCM phù hợp, sau đó loại bỏ logic Xiaomi/HyperOS để xây lại cho ColorOS/OPlus.

Xem `LICENSE` để biết điều khoản MIT và copyright gốc.
