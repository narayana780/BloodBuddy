# Blood Buddy

> **One Drop. A Thousand Hopes.**  
> An emergency blood donor discovery, smart matching, and community blood requisition platform for Rajahmundry and surrounding regions.

---

## Overview

**Blood Buddy** is a Java web application designed to bridge the critical gap between blood donors and patients in urgent need. Built with a responsive black-and-red visual design, Blood Buddy combines an interactive 3D anatomical heart, real-time donor discovery, intelligent biological compatibility matching, private donor-requester messaging, emergency triage boards, and blood donation camp coordination into a single embedded server application.

The application runs on embedded Apache Tomcat with Java Servlets and thread-safe JSON file persistence, requiring zero external database installations for local deployment.

---

## Key Features

### 1. 3D Anatomical Heart & ECG Animation
- **Interactive 3D Heart**: WebGL-powered 3D human heart model (`human_heart.glb`) loaded via Three.js with realistic blood flow visual effects, orbit controls, and auto-rotation.
- **Dynamic Cardiac Fallback**: Automatic, responsive SVG vector fallback with pulse animations for devices without WebGL support.
- **ECG Waveform Monitor**: Real-time SVG cardiac rhythm animation visualizing biological vitality.

### 2. User Authentication & Access Control
- **Unified Portal**: Shared registration and login flow for both blood requesters and prospective donors (`/auth/login`, `/auth/register`).
- **Secure Password Storage**: Passwords hashed using PBKDF2 with SHA-256 and unique cryptographic salt per user.
- **Administrative Security**: Role-based access controls for triage dashboards, camp management, and analytics export (`/admin/login`).

### 3. Donor Discovery & Volunteer Management
- **Donor Registration**: Public registration with donor age verification (18–65), blood group selection, and locality mapping (`/become-donor`).
- **Multi-Criteria Search**: Filter donors by exact blood group, locality, availability status, and eligibility (`/find-donors`).
- **Profile Completion**: Extended donor profile capture including donation frequency, health status, and direct contact options (`/donor/complete-profile`).

### 4. Emergency Blood Requisition Management
- **Request Submission**: Submit blood requests with required units, hospital, locality, emergency contact, and required-by deadline (`/request-blood`).
- **Public Request Board**: Browse live requisitions filtered by urgency tier (Normal, Urgent, Critical) and status (`/view-requests`).
- **Status Lifecycle**: Track requests through `Pending`, `In Progress`, `Fulfilled`, `Completed`, and `Cancelled` states.

### 5. Smart Compatibility & Proximity Matching Engine
- **ABO/Rh Matrix**: Strict biological blood compatibility checking for all 8 standard blood groups (A+, A-, B+, B-, AB+, AB-, O+, O-).
- **Compatible Alternatives**: Transparent recommendation of alternative compatible groups (e.g., universal donor O- for A+ patients).
- **Proximity Ranking**: Locality-aware candidate ordering (same locality ranked highest, followed by adjacent localities) with clear rationale badges.

### 6. Private In-App Chat & Consultation
- **Direct Messaging**: Dedicated, private conversation between blood requesters and prospective donors (`/messages`).
- **Donor Request Inbox**: Prospective donors receive incoming chat requests with one-click Accept or Decline actions.
- **Conversation State Management**: Secure transitions from Pending request to Active conversation, complete with unread indicators and message timestamps.

### 7. Notification Center
- **In-App Alerts**: Automated notifications for request submissions, compatible donor matches, status updates, and chat requests (`/notifications`).
- **Deduplication**: Multi-trigger deduplication ensuring repeated page visits never emit redundant notification records.
- **Persistent State**: Mark-as-read state preserved across server restarts.

### 8. Blood Donation Camps
- **Community Camp Directory**: Browse upcoming and ongoing voluntary donation camps (`/camps`).
- **Volunteer Registration**: Community members can register as camp participants.
- **Coordinator Administration**: Authorized staff can create, update, and manage camp schedules and locations (`/admin/camps`).

### 9. Analytics & Reporting Dashboard
- **Operational Metrics**: Aggregated counts of donors, requisitions, completion rates, and urgency distributions (`/admin/analytics`).
- **Zero-Denominator Safety**: Division protection handling empty datasets and edge cases without runtime failures.
- **Secure CSV Export**: Downloadable reports with spreadsheet formula injection protection (neutralizing `=`, `+`, `-`, `@`, `\t`, `\r` prefixes).

### 10. Emergency Prioritization Center
- **Clinical Triage Queue**: Dynamic urgency board prioritizing Critical requisitions ahead of Urgent and Normal cases (`/admin/emergency`).
- **Time-to-Deadline Monitoring**: Real-time calculation of remaining time with automated overdue detection.
- **2-Hour Due-Soon Alerts**: Automatic detection and highlighting of requisitions approaching deadlines within a configurable threshold.

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Runtime & Language** | Java 21 (JDK 21) |
| **Web Server** | Embedded Apache Tomcat 10.1 (Jakarta Servlet 6.0) |
| **Build & Dependency Management** | Apache Maven with Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| **Persistence** | Thread-safe JSON file storage with `ReentrantReadWriteLock` and atomic file swaps |
| **Frontend Architecture** | Semantic HTML5, Vanilla CSS3 (Custom Black-and-Red Design System), Vanilla JavaScript |
| **3D Graphics & Visuals** | Three.js (WebGL GLTF/GLB Loader), OrbitControls, Canvas Blood Flow Shaders, SVG ECG Waveforms |

---

## Directory Structure

```text
BloodBuddy/
├── .env.example                     # Environment configuration template
├── .gitignore                       # Git ignore rules for build artifacts, IDEs, and local data
├── pom.xml                          # Maven build and dependency configuration
├── mvnw / mvnw.cmd                  # Cross-platform Maven wrapper scripts
├── README.md                        # Project documentation
│
├── data/                            # Active local JSON storage (runtime only, ignored in git)
│   └── .gitkeep                     # Preserves directory in git
├── data.template/                   # Initial storage templates for fresh installations
│   ├── users.json
│   ├── registered_donors.json
│   ├── blood_requests.json
│   ├── camps.json
│   ├── camp_registrations.json
│   ├── conversations.json
│   ├── messages.json
│   └── notifications.json
│
├── docs/                            # Documentation assets
│   └── screenshots/                 # Application renders and visual verifications
│
└── src/
    ├── main/
    │   ├── java/com/bloodbuddy/
    │   │   ├── BloodBuddyServer.java            # Main server entrypoint (Embedded Tomcat)
    │   │   ├── config/
    │   │   │   └── AppConfig.java               # Global configuration and environment settings
    │   │   ├── model/                           # Domain entities and value objects
    │   │   │   ├── AppNotification.java
    │   │   │   ├── BloodCompatibility.java
    │   │   │   ├── BloodRequest.java
    │   │   │   ├── Camp.java
    │   │   │   ├── CampRegistration.java
    │   │   │   ├── Conversation.java
    │   │   │   ├── Donor.java
    │   │   │   ├── DonorMatch.java
    │   │   │   ├── Message.java
    │   │   │   └── User.java
    │   │   ├── repository/                      # Thread-safe JSON persistence repositories
    │   │   │   ├── BloodRequestRepository.java
    │   │   │   ├── CampRegistrationRepository.java
    │   │   │   ├── CampRepository.java
    │   │   │   ├── ConversationRepository.java
    │   │   │   ├── DonorRepository.java
    │   │   │   ├── MessageRepository.java
    │   │   │   ├── NotificationRepository.java
    │   │   │   └── UserRepository.java
    │   │   ├── service/                         # Business logic services
    │   │   │   ├── AnalyticsService.java
    │   │   │   ├── AuthService.java
    │   │   │   ├── BloodRequestService.java
    │   │   │   ├── CampService.java
    │   │   │   ├── ChatService.java
    │   │   │   ├── DonorMatchingService.java
    │   │   │   ├── DonorService.java
    │   │   │   ├── EmergencyPrioritizationService.java
    │   │   │   ├── NotificationService.java
    │   │   │   └── SeedDataProvider.java
    │   │   ├── servlets/                        # HTTP request handlers & controllers
    │   │   │   ├── AdminAuthServlet.java
    │   │   │   ├── AdminCampsServlet.java
    │   │   │   ├── AdminUpdateStatusServlet.java
    │   │   │   ├── AnalyticsApiServlet.java
    │   │   │   ├── AnalyticsDashboardServlet.java
    │   │   │   ├── AnalyticsExportServlet.java
    │   │   │   ├── BecomeDonorServlet.java
    │   │   │   ├── CampsServlet.java
    │   │   │   ├── ChatServlet.java
    │   │   │   ├── CompleteDonorProfileServlet.java
    │   │   │   ├── DashboardServlet.java
    │   │   │   ├── DonorProfileServlet.java
    │   │   │   ├── EmergencyDashboardServlet.java
    │   │   │   ├── FeaturePlaceholderServlet.java
    │   │   │   ├── FindDonorsServlet.java
    │   │   │   ├── HealthCheckServlet.java
    │   │   │   ├── LandingServlet.java
    │   │   │   ├── MatchingServlet.java
    │   │   │   ├── NotificationsServlet.java
    │   │   │   ├── RequestBloodServlet.java
    │   │   │   ├── RequestConfirmationServlet.java
    │   │   │   ├── StaticResourceServlet.java
    │   │   │   ├── UserAuthServlet.java
    │   │   │   └── ViewRequestsServlet.java
    │   │   └── util/                            # Security, hashing & encoding utilities
    │   │       └── PasswordHasher.java
    │   │
    │   └── resources/
    │       ├── static/                          # Frontend stylesheets, scripts, models
    │       │   ├── css/
    │       │   │   ├── style.css
    │       │   │   └── heart-3d.css
    │       │   ├── js/
    │       │   │   ├── main.js
    │       │   │   ├── three.min.js
    │       │   │   ├── GLTFLoader.js
    │       │   │   ├── OrbitControls.js
    │       │   │   └── heart-3d.js
    │       │   └── models/
    │       │       └── human_heart.glb          # 3D Anatomical Heart GLTF Model
    │       └── templates/                       # Server-rendered HTML templates
    │           ├── index.html
    │           ├── find-donors.html
    │           ├── become-donor.html
    │           ├── request-blood.html
    │           ├── view-requests.html
    │           ├── donor-profile.html
    │           ├── complete-donor-profile.html
    │           ├── login.html
    │           ├── register.html
    │           ├── messages.html
    │           ├── chat.html
    │           ├── notifications.html
    │           ├── camps.html
    │           ├── admin-camps.html
    │           ├── admin-login.html
    │           ├── admin-analytics.html
    │           └── admin-emergency.html
    │
    └── test/                                    # Automated verification test suites
        ├── AnalyticsAndReportsTest.java
        ├── BloodRequisitionWorkflowTest.java
        ├── CampsWorkflowTest.java
        ├── DonorChatNotificationEndToEndTest.java
        ├── DonorMatchingAndNotificationTest.java
        ├── DonorRegistrationWorkflowTest.java
        ├── DonorSearchAndUnifiedRegistrationTest.java
        ├── EmergencyPrioritizationTest.java
        ├── SecureChatFeatureTest.java
        ├── SystemIntegrationReleaseTest.java
        ├── UnifiedAuthTest.java
        └── WorkflowAndUiCorrectionTest.java
```

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 21 or higher (LTS recommended).
- **Operating System**: Windows, macOS, or Linux.
- **Network**: Port 8080 open (or custom port via `PORT` environment variable).

---

## Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/blood-buddy.git
cd blood-buddy
```

### 2. Configure Environment (Optional)
Copy `.env.example` to `.env` or set environment variables:
```bash
# Windows PowerShell
$env:PORT="8080"
$env:BLOODBUDDY_ADMIN_USER="admin"
$env:BLOODBUDDY_ADMIN_PASSWORD="your_secure_password"
$env:BLOODBUDDY_DUE_SOON_HOURS="2"

# Linux / macOS
export PORT=8080
export BLOODBUDDY_ADMIN_USER=admin
export BLOODBUDDY_ADMIN_PASSWORD=your_secure_password
export BLOODBUDDY_DUE_SOON_HOURS=2
```

### 3. Initialize Storage
On a fresh checkout, the application will automatically create the `data/` directory upon first launch. If you wish to pre-populate clean empty storage files:
```bash
# Windows PowerShell
Copy-Item -Path "data.template\*" -Destination "data" -Recurse

# Linux / macOS
cp data.template/* data/
```

### 4. Build the Project
Package the self-contained executable runnable JAR using the included Maven wrapper:
```bash
# Windows
.\mvnw.cmd package

# Linux / macOS
./mvnw package
```

### 5. Run the Server
You can launch the server using either the standalone executable JAR or Maven:

**Option A — Standalone Executable JAR (Recommended):**
```bash
# Windows / Linux / macOS
java -jar target/blood-buddy-1.0.0-SNAPSHOT.jar
```

**Option B — Maven Exec Plugin:**
```bash
# Windows
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.BloodBuddyServer"

# Linux / macOS
./mvnw exec:java -Dexec.mainClass="com.bloodbuddy.BloodBuddyServer"
```

Once running, navigate to:
```text
http://localhost:8080/landing
```

---

## Running Test Suites

Blood Buddy includes full automated test suites validating business logic, security safeguards, formula injection defenses, and end-to-end HTTP flows:

```bash
# System Integration & Release Verification (59 verification assertions)
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.SystemIntegrationReleaseTest"

# Smart Donor Matching & Notifications Suite (96 verification assertions)
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.DonorMatchingAndNotificationTest"

# Emergency Triage & Prioritization Suite (11 verification assertions)
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.EmergencyPrioritizationTest"

# Analytics Dashboard & Formula Injection Defense Suite (11 verification assertions)
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.AnalyticsAndReportsTest"

# Live HTTP Donor Chat & Notification Workflow Suite (23 live assertions)
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.DonorChatNotificationEndToEndTest"

# Unified Authentication & Session Security Suite
.\mvnw.cmd exec:java "-Dexec.mainClass=com.bloodbuddy.test.UnifiedAuthTest"
```

---

## Deployment & Containerization

### Docker Deployment (Production Container)

Blood Buddy includes a multi-stage `Dockerfile` that compiles the Maven project and packages it into a minimal JRE 21 runtime container running as a non-privileged user:

```bash
# Build the container image
docker build -t bloodbuddy:latest .

# Run the container (mounting a local persistent data directory)
docker run -d \
  -p 8080:8080 \
  -e PORT=8080 \
  -e HOST=0.0.0.0 \
  -e BLOODBUDDY_ADMIN_USER=admin \
  -e BLOODBUDDY_ADMIN_PASSWORD=your_secure_password \
  -v bloodbuddy-data:/app/data \
  --name bloodbuddy-app \
  bloodbuddy:latest
```

The container includes automated health monitoring targeting `/health` (or `/api/status`).

---

### Deploying to Render from GitHub

Blood Buddy is configured for 1-click deployment on [Render](https://render.com) using `render.yaml`:

1. **Push to GitHub**: Ensure your latest changes are pushed to your `main` branch.
2. **Create Web Service on Render**:
   - Go to the Render Dashboard and click **New +** > **Blueprint**.
   - Connect your GitHub repository (`narayana780/BloodBuddy`).
   - Render detects `render.yaml` and configures the Docker web service automatically.
3. **Configure Environment Variables**:
   - `PORT`: Managed automatically by Render.
   - `HOST`: Set to `0.0.0.0` (configured in `render.yaml`).
   - `BLOODBUDDY_ADMIN_USER`: Set your administrative username.
   - `BLOODBUDDY_ADMIN_PASSWORD`: A strong random password will be auto-generated by Render.
4. **Health Check Endpoint**: Set to `/health` or `/api/status`.

---

### Critical Notice: JSON Data Persistence in Cloud Environments

The application currently uses file-based JSON repositories stored in `data/` (`users.json`, `registered_donors.json`, `blood_requests.json`, `conversations.json`, `messages.json`).

> [!WARNING]
> **Ephemeral Storage on Free Tier**: On Render Free tier (and typical cloud container runtimes), the local filesystem is **ephemeral**. Any donor registrations, accounts, or private messages saved to `/app/data` during runtime will be wiped whenever the service idles, restarts, or is redeployed.

#### Safe Persistence Options:

1. **Render Persistent Disk (Starter Tier & Above)**:
   - Attach a persistent disk to the Render service mounted at `/app/data` by uncommenting the `disk:` block in `render.yaml`.
   - The disk retains JSON files across restarts and redeploys without code modifications.
2. **Production Database Migration (Recommended for Long-Term Scale)**:
   - For high availability, concurrent scaling, and automated backups, migrate from JSON file storage to a managed relational database (e.g., PostgreSQL on Render or Supabase).
   - A database migration enables zero-downtime rolling deploys, horizontal replica scaling, and ACID guarantees.

---

## Data Protection & Security Notice

- **Private Donor Data**: The `data/` directory stores local application records (user credentials, phone numbers, chat logs). All `data/*.json` files are excluded from Git version control via `.gitignore`.
- **Safe Initialization**: Clean templates (`data.template/`) provide the baseline JSON schema for new checkouts without exposing personal data.
- **Spreadsheet Security**: CSV exports automatically sanitize strings starting with formula trigger characters (`=`, `+`, `-`, `@`, `\t`, `\r`) to protect administrators from CSV injection attacks.
- **Access Guards**: Sensitive administrative actions and donor triage boards require valid session authentication.

---

## License

All rights reserved by the project author. For licensing inquiries or distribution permissions, please contact the repository owner.
