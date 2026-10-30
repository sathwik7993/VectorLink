# VectorLink — Azure Deployment (single VM, shared with other projects)

One Ubuntu VM runs Docker Compose: shared Postgres + Redis, one container per app.
Caddy container terminates TLS (auto Let's Encrypt) and routes by domain. Replaces the AWS guide in `DEPLOYMENT.md`.

## 1. Credits
- Claim Azure for Students at azure.microsoft.com/free/students (about $100, no card).
- Cost Management → Budgets: alerts at $50 and $80.
- Idle VM: `az vm deallocate -g vectorlink-rg -n vectorlink-vm` (stops compute billing). Restart: `az vm start ...`.

## 2. Create the VM (Azure CLI)
```bash
az login
az group create -n vectorlink-rg -l centralindia
az vm create -g vectorlink-rg -n vectorlink-vm \
  --image Ubuntu2204 --size Standard_B2s \
  --admin-username azureuser --generate-ssh-keys \
  --public-ip-sku Standard --os-disk-size-gb 64
az vm open-port -g vectorlink-rg -n vectorlink-vm --port 80 --priority 1010
az vm open-port -g vectorlink-rg -n vectorlink-vm --port 443 --priority 1020
az vm show -d -g vectorlink-rg -n vectorlink-vm --query publicIps -o tsv
```
Port 22 is open by default. Restrict it to your IP in the NSG. If the size is unavailable in the region, list sizes with `az vm list-skus -l centralindia --size Standard_B -o table`.
DNS (both to the VM public IP):
- DuckDNS: set `vectorlink.duckdns.org` IP.
- name.com: A record `@` and `www` for `arch-sim.dev`. Remove old AWS records.

## 3. Prepare the VM
```bash
ssh azureuser@<ip>
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker azureuser && newgrp docker
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
mkdir -p ~/stack/init-db ~/apps
git clone https://github.com/sathwik7993/VectorLink.git ~/apps/vectorlink
git clone https://github.com/sathwik7993/ArchSim.git ~/apps/designlab
```
Private repos: use a GitHub PAT in the clone URL.

## 4. Stack files (from your PC, repo root)
```bash
scp infrastructure/azure/{docker-compose.yml,Caddyfile} azureuser@<ip>:~/stack/
scp infrastructure/azure/.env.example azureuser@<ip>:~/stack/.env
scp infrastructure/azure/00-create-dbs.sh infrastructure/init-db/01-schema.sql azureuser@<ip>:~/stack/init-db/
```
Edit `~/stack/.env` on the VM: strong passwords, Google creds, `ARCHSIM_TOKEN_SECRET=$(openssl rand -base64 48)`.
Never copy `vectorlink.pem`. `*.pem` is in `.gitignore`.

## 5. Start
```bash
cd ~/stack && docker compose up -d --build   # first build is slow (Maven); build one service at a time if RAM is tight
docker compose ps
docker compose logs -f vectorlink archsim-backend
```
`init-db` runs only on first start of an empty volume. `docker compose down -v` DELETES data.
ArchSim schema comes from Flyway. VectorLink schema comes from `01-schema.sql`.

## 6. Google OAuth
Add `https://vectorlink.duckdns.org/login/oauth2/code/google` (check your Spring redirect path) to the authorized redirect URIs.

## 7. Update an app
Automatic: every push to `main` that passes CI runs the `deploy-azure` job in `.github/workflows/ci-cd.yml`.
It SSHes into the VM, pulls, rebuilds `vectorlink` (removing containers for services no longer in the compose file), and waits for `/actuator/health` on the internal management port 9091 to report `UP`.
Enable it by adding these repository secrets: `AZURE_VM_HOST` (public IP), `AZURE_VM_USER` (`azureuser`), `AZURE_VM_SSH_KEY` (private key).
Without them the job is skipped.

Manual:
```bash
cd ~/apps/vectorlink && git pull && cd ~/stack && docker compose up -d --build --remove-orphans vectorlink
```

`~/stack/.env` must define `JWT_SECRET` (at least 32 bytes, `openssl rand -base64 48`). The prod profile refuses to start without it.

## Memory (4 GB VM)
Postgres 300m + Redis 100m + Caddy 60m + VectorLink 600m + ArchSim 650m + OS ≈ 2.2 GB. Swap covers build spikes.
No Kafka broker runs in production (it would add about 512 MB). `vectorlink` uses `KAFKA_ENABLED: "false"`, so clicks are written straight to Postgres.
Everything else is unchanged: Redis caching, analytics dashboards, live SSE click stream, rate limiting, auth.
The Kafka pipeline (batch consumer, dead-letter topic, replay) still runs in the local `docker-compose.yml` stack and in CI.
If an older deploy left a `kafka` container running, `--remove-orphans` (above) removes it.
