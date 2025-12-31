# Windows Setup Guide - Portfolio Management System

> **Complete installation guide for running the Portfolio Management System on Windows**

## 📋 Required Software

1. **Java 17 JDK** - For running Spring Boot applications
2. **Maven** - For building Java projects
3. **Docker Desktop** - For containers and Docker Compose
4. **Node.js** - For React frontend
5. **Git** - For version control (optional but recommended)

---

## 🚀 Recommended: Using Chocolatey Package Manager

**Chocolatey** is the most reliable package manager for Windows and works with all required tools.

### Step 1: Install Chocolatey

Open **PowerShell (Run as Administrator)** - Right-click Start Menu → Windows PowerShell (Admin)

Copy and paste this command:

```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```

Wait for installation to complete (30-60 seconds).

### Step 2: Verify Chocolatey is installed

```powershell
choco --version
```

You should see a version number (e.g., `1.4.0`).

### Step 3: Install all required tools

**Keep the same PowerShell window (Run as Administrator)** and run these commands one by one:

```powershell
# Install Java 17 JDK
choco install temurin17 -y

# Install Maven
choco install maven -y

# Install Docker Desktop
choco install docker-desktop -y

# Install Node.js LTS
choco install nodejs-lts -y

# Install Git (optional but recommended)
choco install git -y
```

**Note**: Each installation takes 1-3 minutes. The `-y` flag auto-confirms prompts.

### Step 4: Refresh environment

**IMPORTANT**: Close PowerShell completely and open a **NEW PowerShell window** to refresh environment variables.

### Step 5: Verify installations

In the new PowerShell window, run:

```powershell
# Check Java
java -version
# Expected output: openjdk version "17.0.x"

# Check Maven
mvn -version
# Expected output: Apache Maven 3.9.x

# Check Docker
docker --version
docker-compose --version
# Expected output: Docker version 24.x.x / Docker Compose version v2.x.x

# Check Node.js
node --version
npm --version
# Expected output: v20.x.x and 10.x.x

# Check Git
git --version
# Expected output: git version 2.x.x
```

---

## 🔧 Alternative Method: Using winget (if Chocolatey doesn't work)

If you prefer winget:

### Install winget tools

Open **PowerShell (Run as Administrator)** and run:

```powershell
# Install Java 17 (Eclipse Temurin)
winget install EclipseAdoptium.Temurin.17.JDK

# Install Docker Desktop
winget install Docker.DockerDesktop

# Install Node.js LTS
winget install OpenJS.NodeJS.LTS

# Install Git (optional but recommended)
winget install Git.Git
```

**Note**: winget doesn't reliably install Maven, so use Chocolatey for Maven even if using winget for others:

```powershell
choco install maven -y
```

---

## 📦 Manual Installation (Fallback Option)

If package managers don't work, download and install manually:

### 1. Java 17 JDK
- **Download**: https://adoptium.net/temurin/releases/?version=17
- Choose: Windows x64 MSI installer
- Run installer with default options
- **Verify**: Open new PowerShell → `java -version`

### 2. Maven
- **Download**: https://maven.apache.org/download.cgi
- Choose: Binary zip archive (apache-maven-3.9.x-bin.zip)
- Extract to `C:\Program Files\Apache\maven`
- **Add to PATH manually**:
  1. Search "Environment Variables" in Windows
  2. Edit "Path" under System Variables
  3. Add: `C:\Program Files\Apache\maven\bin`
- **Verify**: Open new PowerShell → `mvn -version`

### 3. Docker Desktop
- **Download**: https://www.docker.com/products/docker-desktop/
- Choose: Docker Desktop for Windows
- Run installer
- **Important**: Start Docker Desktop after installation
- Enable WSL 2 if prompted
- **Verify**: `docker --version` and `docker-compose --version`

### 4. Node.js
- **Download**: https://nodejs.org/
- Choose: LTS version (20.x)
- Run installer with default options
- **Verify**: `node --version` and `npm --version`

### 5. Git
- **Download**: https://git-scm.com/download/win
- Run installer with default options
- **Verify**: `git --version`

---

## ⚙️ Post-Installation Configuration

### 1. Configure Docker Desktop

After installing Docker Desktop:

1. **Start Docker Desktop** from Start Menu
2. Wait for Docker to start (icon in system tray will stop animating)
3. Accept the service agreement if prompted
4. **Enable WSL 2** (recommended):
   - Settings → General → Use WSL 2 based engine
5. **Allocate Resources** (Settings → Resources):
   - CPUs: 4 (minimum 2)
   - Memory: 8 GB (minimum 4 GB)
   - Disk: 60 GB

### 2. Configure Maven (Optional but recommended)

Create Maven settings directory:

```powershell
mkdir $env:USERPROFILE\.m2
```

Create a `settings.xml` file in `C:\Users\YourUsername\.m2\settings.xml`:

```xml
<settings>
  <localRepository>C:/Users/YourUsername/.m2/repository</localRepository>
</settings>
```

### 3. Verify Docker is working

```powershell
# Test Docker
docker run hello-world

# Expected output: "Hello from Docker!"
```

---

## 🎯 Project Setup - Quick Start

Once everything is installed:

### 1. **IMPORTANT: Start Docker Desktop FIRST**

Before running any Docker commands, you **MUST** start Docker Desktop:

1. Search for "Docker Desktop" in Windows Start Menu
2. Click to launch Docker Desktop
3. **Wait 1-2 minutes** for Docker to fully start
4. Look for the Docker whale icon in your system tray (bottom-right)
5. The icon should be **still** (not animating) - this means Docker is ready

**To verify Docker is running:**

```powershell
docker ps
```

If you see a table (even if empty), Docker is ready. If you see an error about "daemon not running", wait longer.

### 2. Navigate to project directory

```powershell
cd C:\workspace\pms-2025-12-30
```

### 3. Build the project

```powershell
mvn clean install -DskipTests
```

This will:
- Download all Maven dependencies (first time will take 5-10 minutes)
- Compile all Java code
- Package applications into JAR files

### 3. Start infrastructure services

```powershell
docker-compose up -d mongodb-portfolio mongodb-transaction redis zookeeper kafka
```

Wait 30-60 seconds for services to start, then verify:

```powershell
docker-compose ps
```

All services should show "running" and "healthy".

### 4. Start backend services

Open **4 separate PowerShell windows** and run one command in each:

**Window 1 - Portfolio Service:**
```powershell
cd C:\workspace\pms-2025-12-30\portfolio-service
mvn spring-boot:run
```

**Window 2 - Transaction Service:**
```powershell
cd C:\workspace\pms-2025-12-30\transaction-service
mvn spring-boot:run
```

**Window 3 - Notification Service:**
```powershell
cd C:\workspace\pms-2025-12-30\notification-service
mvn spring-boot:run
```

**Window 4 - API Gateway:**
```powershell
cd C:\workspace\pms-2025-12-30\api-gateway
mvn spring-boot:run
```

Wait for each service to show: `Started [ServiceName]Application in X seconds`

### 5. Start frontend

Open another PowerShell window:

```powershell
cd C:\workspace\pms-2025-12-30\frontend
npm install
npm run dev
```

### 6. Access the application

- **Frontend Dashboard**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Portfolio API Docs**: http://localhost:8081/api/portfolio/swagger-ui.html
- **Transaction API Docs**: http://localhost:8082/api/transaction/swagger-ui.html

---

## 🐛 Troubleshooting

### Issue: "winget is not recognized"

**Solution**: Update Windows or install App Installer from Microsoft Store:
- Open Microsoft Store
- Search for "App Installer"
- Install/Update it

### Issue: "Docker daemon is not running" or "cannot find file specified"

**This is the most common issue!**

**Solution**:
1. **Open Docker Desktop** from Start Menu (search "Docker Desktop")
2. **Wait for it to fully start** (1-2 minutes)
   - Watch the Docker whale icon in system tray
   - Icon should stop animating when ready
3. **Verify Docker is running**:
   ```powershell
   docker ps
   ```
   If you see a table, Docker is ready!
4. Try your command again

**If Docker Desktop won't start**:
- Check if Hyper-V/WSL 2 is enabled in Windows Features
- Restart your computer
- Reinstall Docker Desktop: `choco uninstall docker-desktop -y` then `choco install docker-desktop -y`

### Issue: "Maven not found" or "Java not found"

**Solution**:
1. Close all PowerShell windows
2. Open a new PowerShell window
3. Try again (environment variables need refresh)

If still not working, manually add to PATH:
1. Search "Environment Variables" in Windows
2. Edit "Path" under System Variables
3. Add Java: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x\bin`
4. Add Maven: `C:\Program Files\Apache\maven\bin`
5. Restart PowerShell

### Issue: Port already in use

**Solution**: Find and kill the process using the port

```powershell
# Find process on port 8080 (example)
netstat -ano | findstr :8080

# Kill the process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

### Issue: Docker build fails with "no space left on device"

**Solution**: Clean up Docker

```powershell
docker system prune -a --volumes
```

### Issue: Maven build fails with "connection timeout"

**Solution**: Configure Maven to use different mirror

Edit `C:\Users\YourUsername\.m2\settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>maven-central</id>
      <mirrorOf>central</mirrorOf>
      <url>https://repo1.maven.org/maven2</url>
    </mirror>
  </mirrors>
</settings>
```

---

## 📊 System Requirements

### Minimum Requirements
- **OS**: Windows 10 (1809+) or Windows 11
- **RAM**: 8 GB
- **CPU**: 4 cores
- **Disk**: 20 GB free space
- **Internet**: Required for downloads

### Recommended Requirements
- **OS**: Windows 11
- **RAM**: 16 GB
- **CPU**: 6+ cores
- **Disk**: 40 GB free space (SSD preferred)
- **Internet**: High-speed connection

---

## ✅ Installation Checklist

Use this checklist to track your progress:

- [ ] Install Java 17 JDK
- [ ] Install Maven
- [ ] Install Docker Desktop
- [ ] Install Node.js
- [ ] Install Git (optional)
- [ ] Verify all installations
- [ ] Start Docker Desktop
- [ ] Configure Docker resources
- [ ] Build Maven project
- [ ] Start infrastructure services
- [ ] Start backend services
- [ ] Start frontend
- [ ] Access dashboard at http://localhost:3000

---

## 🎓 Next Steps

After installation is complete:

1. **Read the API Examples**: See `API-EXAMPLES.md` for sample API calls
2. **Explore Swagger UI**: Interactive API documentation
3. **Run Tests**: `mvn test` to run unit tests
4. **Check Logs**: Monitor service logs in PowerShell windows
5. **Stop Services**: Press `Ctrl+C` in each PowerShell window

---

## 📞 Need Help?

If you encounter issues not covered here:

1. Check Docker Desktop logs: Settings → Troubleshoot → View logs
2. Check service logs in PowerShell windows
3. Verify all services are running: `docker-compose ps`
4. Ensure ports are not in use: `netstat -ano | findstr ":8080"`

---

**Installation Time Estimate**: 30-45 minutes (including downloads)

**Good luck! 🚀**
