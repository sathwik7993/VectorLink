# 🚀 VectorLink — AWS & DuckDNS Production Deployment Guide

This guide provides step-by-step instructions to deploy VectorLink on the **AWS Free Tier** (EC2 + RDS) using **DuckDNS** for a free custom subdomain.

---

## 🏗️ Deployment Architecture

* **Host Machine**: AWS EC2 instance (`t3.micro` or `t2.micro` - 1 GB RAM, Ubuntu LTS).
* **Database**: AWS RDS PostgreSQL instance (`db.t3.micro` - 1 GB RAM, 20 GB SSD).
* **Cache**: Native Redis Server running on the EC2 instance.
* **Domain Name**: Free custom subdomain via **DuckDNS** (e.g., `vectorlink.duckdns.org`).
* **Reverse Proxy & SSL**: Nginx routing port 80/443 to Spring Boot on port 8080.
* **Resilience**: A 2 GB virtual swap file on EC2 to protect against Out-Of-Memory (OOM) crashes.

---

## 📋 Prerequisites

1. An active **AWS Account** (Free Tier eligible).
2. A free account on **[DuckDNS.org](https://www.duckdns.org/)** (no credit card required).

---

## 🏁 Step 1: Set Up AWS RDS (PostgreSQL Database)

1. Log into your **AWS Management Console** and search for **RDS**.
2. Click **Create database** and choose:
   - **Database creation method**: Standard create
   - **Engine type**: PostgreSQL
   - **Templates**: **Free Tier** *(forces db.t3.micro / db.t2.micro and 20 GB storage)*
3. **Settings**:
   - DB instance identifier: `vectorlink-db`
   - Master username: `postgres`
   - Master password: `<Choose a strong password>` (save this password)
4. **Connectivity**:
   - **Public Access**: Select **No** (keeps the database private from the public internet).
5. Click **Create database**.
6. Once the database status changes to **Available**, click it and copy the **Endpoint** URL under the *Connectivity & security* tab (e.g., `vectorlink-db.xxxx.us-east-1.rds.amazonaws.com`).

---

## 🏁 Step 2: Set Up your Free Subdomain on DuckDNS

1. Log into **[DuckDNS.org](https://www.duckdns.org/)** using GitHub, Google, or Reddit.
2. In the domain creation field, enter your preferred name (e.g., `vectorlink-demo`) and click **add domain**.
3. Keep the DuckDNS tab open; we will update the IP address once the AWS EC2 instance is launched.

---

## 🏁 Step 3: Launch AWS EC2 Instance

1. Go to the **EC2 Console** on AWS and click **Launch instance**.
2. **Name**: `vectorlink-server`
3. **OS (AMI)**: **Ubuntu Server 22.04 LTS** or **24.04 LTS** (64-bit x86).
4. **Instance Type**: **`t3.micro`** (or `t2.micro` depending on region) - marked **Free tier eligible**.
5. **Key Pair**: Create or select an existing `.pem` key pair and download it.
6. **Network Settings**:
   - Create a security group.
   - Allow **SSH** (Port 22) traffic from **My IP** only (for security).
   - Allow **HTTP** (Port 80) traffic from **Anywhere**.
   - Allow **HTTPS** (Port 443) traffic from **Anywhere**.
7. Click **Launch instance**.
8. Go to your EC2 instances list, copy the **Public IPv4 address** of your new server, and paste it into your DuckDNS dashboard under **IP address** next to your domain, then click **update IP**.

---

## 🏁 Step 4: Configure the EC2 Server

Connect to your EC2 instance via SSH:
```bash
ssh -i "your-key.pem" ubuntu@<your-ec2-public-ip>
```

#### 1. Configure Swap Space (Crucial Safety Net)
Since the free EC2 instance only has 1 GB of RAM, configuring 2 GB of swap space prevents the server from crashing during memory peaks:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

#### 2. Install Required Software
Install Java 17, native Redis Cache, Nginx, and the PostgreSQL CLI client:
```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless redis-server nginx postgresql-client
```

---

## 🏁 Step 5: Authorize EC2 Connection in Database
1. Go to the **RDS Database Console** → **Connectivity & security**.
2. Click on the **VPC security groups** link for the database.
3. Under **Inbound Rules**, click **Edit inbound rules**.
4. Add a rule:
   - **Type**: PostgreSQL (Port 5432)
   - **Source**: Select the **Security Group of your EC2 instance**.
   - Click **Save rules**.

---

## 🏁 Step 6: Deploy the Database Schema

Because the production configuration validates the schema (`ddl-auto: validate`) rather than mutating it automatically, you must initialize the tables before launching the app:

1. **Upload the DDL schema file** `01-schema.sql` to your EC2 server from your local project root:
   ```bash
   scp -i "your-key.pem" infrastructure/init-db/01-schema.sql ubuntu@<your-ec2-public-ip>:~/
   ```
2. **Execute the schema SQL** on your RDS PostgreSQL instance from the EC2 terminal:
   ```bash
   psql -h <YOUR-RDS-ENDPOINT> -U postgres -d postgres -f /home/ubuntu/01-schema.sql
   ```
   *(Enter your RDS master password when prompted. This will create all users, tables, and partitions.)*

---

## 🏁 Step 7: Deploy the Spring Boot Application

1. **Build the JAR file** locally in your project folder:
   ```bash
   mvn clean package -DskipTests
   ```
2. **Upload the JAR** to the EC2 server:
   ```bash
   scp -i "your-key.pem" target/url-shortener-0.0.1-SNAPSHOT.jar ubuntu@<your-ec2-public-ip>:~/
   ```
3. **Configure systemd Service** to run the JAR in the background:
   ```bash
   sudo nano /etc/systemd/system/vectorlink.service
   ```
   Paste the following configuration:
   ```ini
   [Unit]
   Description=VectorLink URL Shortener Service
   After=syslog.target network.target redis-server.service

   [Service]
   User=ubuntu
   ExecStart=/usr/bin/java -Xmx350m -Dspring.profiles.active=prod -Dserver.port=8080 -DDB_HOST=<YOUR-RDS-ENDPOINT> -DDB_NAME=postgres -DDB_USER=postgres -DDB_PASS=<YOUR-RDS-PASSWORD> -DREDIS_HOST=127.0.0.1 -DREDIS_PORT=6379 -jar /home/ubuntu/url-shortener-0.0.1-SNAPSHOT.jar
   SuccessExitStatus=143
   Restart=always
   RestartSec=10

   [Service]
   # Environmental fallback values
   Environment=GOOGLE_CLIENT_ID=google-client-id
   Environment=GOOGLE_CLIENT_SECRET=google-client-secret

   [Install]
   WantedBy=multi-user.target
   ```
   *Note: Replace `<YOUR-RDS-ENDPOINT>` and `<YOUR-RDS-PASSWORD>` with your RDS details.*

4. **Start and enable the service**:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable vectorlink
   sudo systemctl start vectorlink
   ```
5. **Verify the service status**:
   ```bash
   sudo systemctl status vectorlink
   ```

---

## 🏁 Step 8: Configure Nginx Reverse Proxy & SSL

1. Open the Nginx default config:
   ```bash
   sudo nano /etc/nginx/sites-available/default
   ```
2. Replace the `location /` block inside `server` with the following configuration:
   ```nginx
   location / {
       proxy_pass http://localhost:8080;
       proxy_set_header Host $host;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
   }
   ```
3. Test and restart Nginx:
   ```bash
   sudo nginx -t
   sudo systemctl restart nginx
   ```
4. **Configure HTTPS (SSL)**:
   Ensure your DuckDNS domain is working and pointing to the correct IP, then run:
   ```bash
   sudo apt install -y certbot python3-certbot-nginx
   sudo certbot --nginx -d <yourname>.duckdns.org
   ```

Your platform is now fully deployed and live at `https://<yourname>.duckdns.org`!
