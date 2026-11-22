YouTube VPN — Android-приложение для маршрутизации только официального приложения YouTube через WireGuard.

Требования: Android 8.0+ (API 26), действующий конфигурационный файл WireGuard .conf от VPN-провайдера или собственного сервера.

Сборка: GitHub Actions автоматически собирает debug APK при push в main/master. APK публикуется как артефакт YouTubeVPN-debug-apk в разделе Actions.

Важно: приложение не предоставляет VPN-сервер и не содержит встроенных серверных ключей. После установки импортируйте собственный WireGuard .conf. Приложение добавляет IncludedApplications = com.google.android.youtube в секцию [Interface], чтобы туннель обслуживал только YouTube.
