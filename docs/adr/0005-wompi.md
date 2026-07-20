# Wompi

Tools to resolve issues:

curl -sSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc \
  | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null

echo "deb https://ngrok-agent.s3.amazonaws.com buster main" \
  | sudo tee /etc/apt/sources.list.d/ngrok.list

sudo apt update
sudo apt install ngrok

Current limitations
- Stripe is currently not available on El Salvador and most central american countries.
- Wompi doesn't handle recurring subscriptions for annual payments.
- Wompi SV documentation requires updates, last update was 3 years ago.
- Recurring transactions cannot be handled by code.
- On github there is not enough public implementations for payment walls in El Salvador.