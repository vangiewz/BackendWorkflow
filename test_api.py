import urllib.request, urllib.error, json

req = urllib.request.Request(
    'https://api-sandbox.nowpayments.io/v1/invoice', 
    data=json.dumps({
        'price_amount': 10, 
        'price_currency': 'usd', 
        'pay_currency': 'usdttrex', 
        'order_id': 'test1234', 
        'order_description': 'test', 
        'ipn_callback_url': 'http://localhost:8080/api/webhooks/nowpayments'
    }).encode(), 
    headers={
        'x-api-key': 'Y38JB0H-ZEW4231-P0DZ4GB-GP4FZHA', 
        'Content-Type': 'application/json',
        'User-Agent': 'Mozilla/5.0'
    }, 
    method='POST'
)

try:
    resp = urllib.request.urlopen(req)
    print(resp.read().decode())
except urllib.error.HTTPError as e:
    print(e.read().decode())
