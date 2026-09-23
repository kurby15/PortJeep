# PortJeep - System Flow & Architecture Documentation

This document provides a comprehensive overview of the end-to-end system flow, navigation structure, and architectural implementation of the **PortJeep** Android application.

---

## 1. System Overview
PortJeep is a specialized mobile solution for drivers and Public Assistance Officers (PAOs) to manage operational schedules, track financial remittances, and maintain secure digital identities. The app follows a **Single-Activity Architecture** built with **Java**, leveraging Firebase for real-time services and a custom Vercel REST API for complex data processing.

---

## 2. Authentication & Secure Access Flow

### A. Initialization & Session Recovery (`SplashLoading`)
1. **Entry Point**: The application starts at `SplashLoading`.
2. **Session Persistence**: It immediately checks for an active Firebase session.
3. **Smart Routing**:
   - **Authenticated**: Transitions directly to `MainActivity` after a brief branding delay.
   - **Unauthenticated**: Directs to `LogInActivity` for credential entry.

### B. Login Security & Authorization (`LogInActivity`)
1. **Multi-Layer Security**:
   - **reCAPTCHA Enterprise**: Integrated to prevent automated bot attacks.
   - **Biometric Integration**: Supports hardware-backed authentication (Fingerprint/Face) using `androidx.biometric`. Credentials are encrypted and stored locally via **Jetpack Security**.
2. **Authorization Engine (RBAC)**:
   - Post-login, the app verifies user roles (`DRIVER`, `PAO`, or `PUBLIC ASSISTANT`) against the Firestore `File201` collection.
   - Unauthorized access attempts are blocked, and the session is terminated.
3. **Credential Recovery**:
   - **External Reset**: The "Forgot Password?" flow dispatches a Firebase-managed recovery link to the user's registered email.

---

## 3. Core Workspace Architecture (`MainActivity`)

`MainActivity` acts as the primary navigation host, managing four top-level functional modules via a `BottomNavigationView`.

```
                  +-----------------------------------+
                  |         SplashLoading             |
                  +-----------------+-----------------+
                                    |
                    Session Active? |
                   +----------------+----------------+
                   |                                 |
                [ YES ]                           [ NO ]
                   |                                 |
                   v                                 v
         +-------------------+             +-------------------+
         |   MainActivity    |             |   LogInActivity   +-----------+
         +---------+---------+             +---------+---------+           |
                   |                                 |                     |
                   |                +----------------+              [ Forgot Password? ]
                   |                |                                      |
                   |          [ Successful Auth ]                          v
                   |          [ Role Verification ]            [ Reset Password Dialog ]
                   |                |                               (Recovery Link)
                   | <--------------+
                   v
     +-------------------------------------------------------------+
     | Tab Navigation (BottomNavigationView inside MaterialCard)    |
     +-----+---------------+---------------+---------------+-------+
           |               |               |               |
           v               v               v               v
     [ HomeTab ]     [ ScheduleTab ]  [ SalaryTab ]   [ ProfileTab ]
                                                           |
                                                           v
                                                  +-----------------+
                                                  | Settings Screen |
                                                  +--------+--------+
                                                           |
                                           +---------------+---------------+
                                           |               |               |
                                   [ Update Email ] [ Change Mobile ] [ Change Password ]
                                    (Maintenance)    (Maintenance)      (Internal Reset)
                                                                           |
                                                                           v
                                                                   [ Re-authenticate ]
                                                                           |
                                                                           v
                                                                 [ Strength Validation ]
                                                                           |
                                                                           v
                                                                   [ Success & Logout ]
```

### A. Advanced Navigation UX
- **Dynamic Layout Insets**: The UI automatically adjusts bottom margins using `WindowInsetsCompat` to handle modern gesture navigation vs. traditional 3-button bars.
- **Fragment Management**: Uses a `hide/show` transaction strategy to preserve the state of fragments when switching tabs.
- **Tab History & Back-Press**: Tracks tab navigation in an `ArrayList`. The back-press logic retrogressively steps through the history before returning to `HomeTab`, ensuring the user doesn't accidentally exit the app.

### B. Workspace Security Features
- **Internal Password Reset**: A high-integrity flow in `ChangePasswordActivity` requiring re-authentication and real-time auditing of password strength (casing, numbers, and special symbols like £, ÷, ×).
- **Biometric Preference Management**: Toggling biometric login in `SettingsFragment` requires a password check before committing changes to `EncryptedSharedPreferences`.
- **Feature Maintenance**: Sensitive account updates (`UpdateEmailActivity`, `ChangeMobileActivity`) include a **Maintenance State** toggle that displays a specialized overlay for controlled rollout.

---

## 4. Operational Feature Modules

### 1. Home Dashboard (`com.example.portjeep.home`)
- **Real-time Announcements**: Uses a `StatusBannerAdapter` for critical operational notifications.
- **Status Visibility**: Drivers can quickly view their current active assignment status.

### 2. Schedule Management (`com.example.portjeep.schedule`)
- **API Connectivity**: Fetches live shift data from a Vercel-hosted REST endpoint (`/api/mobile/schedules`).
- **Data Protection**: Performs on-device decryption of PII (driver/PAO details) using **AES-256**.
- **Categorized View**: Segregates data into **Previous**, **Today**, and **Upcoming** using `ViewPager2`.

### 3. Salary & Earnings Tracker (`com.example.portjeep.salary`)
- **Financial Logic**: Connects to the `/api/mobile/remittances` API to aggregate payout data.
- **Offline Optimization**: Implements a 1-hour caching policy to ensure data availability during intermittent connectivity.

---

## 5. Background Infrastructure & Security

### A. Lifecycle Management
- **Push Notifications (FCM)**: Real-time dispatching via `MyFirebaseMessagingService`.
- **Automated Alarms**: `ScheduleAlarmReceiver` triggers the system `AlarmManager` for daily shift reminders, even if the app is killed.

### B. Data & Network Security
- **Encryption**: Uses `CryptoUtils` for data-in-transit protection and **Jetpack Security** for data-at-rest encryption.
- **API Guard**: All external REST calls require a valid Firebase ID Token passed via Bearer authorization headers.
- **Connectivity Awareness**: Built-in network listeners that show a `NoInternetFragment` when connectivity is lost during critical operations.

---

## 6. Architectural Summary
- **Primary Stack**: Java, Firebase (Auth, Firestore, FCM), Vercel REST API.
- **Design Patterns**: Single-Activity, MVVM, Adapter, Observer.
- **UI/UX**: Material Design 3, Lottie, Shimmer, Glide.
- **Persistence**: Encrypted SharedPreferences, JSON Caching.
