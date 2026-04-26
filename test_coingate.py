import urllib.request, urllib.error, json

req = urllib.request.Request(
    'https://api-sandbox.coingate.com/v2/orders', 
    data=json.dumps({
        'price_amount': 10.5, 
        'price_currency': 'USD', 
        'receive_currency': 'USDT', 
        'order_id': 'test12345', 
        'title': 'Test Order', 
        'callback_url': 'https://example.com/callback'
    }).encode(), 
    headers={
        'Authorization': 'Token UQ5zikXs1zTykEvRuxGb8xoKboBydFESCwHW7FGU', 
        'Content-Type': 'application/json'
    }, 
    method='POST'
)

try:
    resp = urllib.request.urlopen(req)
    print(resp.read().decode())
except urllib.error.HTTPError as e:
    print("HTTP Error:", e.code)
    print(e.read().decode())
