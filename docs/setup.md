## Getting started

### Requirements
- Docker Desktop (you can install it from [Docker](https://www.docker.com/products/docker-desktop/))
- VSCode
- Dev Containers extension [here](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

### Setup

- Open Docker Desktop during the process, this is important.
- Open VSCode and open the project folder
- A notification will appear on the corner
- Click on the "Reopen in Container" option, then wait until everything is setup. (This may take 8–10 minutes on first run)
- Watch the containers in Docker Desktop, everyone has to run. (The status is now green on docker)
- Once everything is downloaded you can run the application with the following command:

```bash
    ./mvnw spring-boot:run
```

### Ngrok install for wompi testing:

curl -sSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null && \
echo "deb https://ngrok-agent.s3.amazonaws.com buster main" | sudo tee /etc/apt/sources.list.d/ngrok.list && \
sudo apt update && \
sudo apt install ngrok -y

ngrok config add-authtoken yourToken
ngrok http 8080

## Troubleshooting during setup

### My containers had trouble:

Try this first:
- Close all VSCode windows and Docker Desktop, 
- Reopen Docker Desktop first and then VSCode
- Open the folder project
- Click on the notification
- Wait again and see if the containers are running on Docker Desktop

If that didnt work usually is because Docker sometimes requires you to restart your machine and try the steps I wrote before.

### I changed my .env variables and they doesn't work
- You can run "echo $ENV_VAR" (Example: "echo $DB_URL") to display how the container reads the variable.
- If there is an older value press F1 or CTRL + SHIFT + P and run "Dev Containers: Rebuild Container"
- Run "echo $ENV_VAR" again before run the app and now you can see if it reads the value you changed
