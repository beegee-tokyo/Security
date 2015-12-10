import select, socket, subprocess

port = 5000  # where do you expect to get a msg?
bufferSize = 1024 # whatever you need

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('', port))
s.setblocking(0)

while True:
    result = select.select([s],[],[])
    msg = result[0][0].recv(bufferSize) 
    msgParts = msg.split(",")
    if msgParts[2] == '1':
        print ("Alarm - Intruder")
        subprocess.call(["amixer", "cset", "numid=3", "1"])
        subprocess.call(["mplayer", "-volume", "100", "-loop", "5", "/media/TERESA/dog.mp3"])
        subprocess.call(["amixer", "cset", "numid=3", "2"])

