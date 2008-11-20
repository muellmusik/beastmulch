#!/usr/bin/python
#
#	squark.py - new gluion configuration tool (v1)
#


from string import *
from types import *
import sys, os, socket, time, thread, re

import OSC




#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	utility functions
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def printHeader ():
	print "squark - the gluion configuration tool (v1)"
	print "==========================================="
	print ""
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def glear ():
#	print ""
#	print ""
#	print ""
##	print "-----------------------------------------"
	#print ""
#	print ""
#	print ""
#	print ""
#	print ""
#	print ""
#	print ""
#	return
	if os.name=="posix":
		os.system('clear')
	elif os.name in ("nt", "dos", "ce"):
		os.system ('CLS')
	else:
		print '\n' * 100				
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def cls ():
	glear ()
	printHeader ()

	
def splitIP (IPstring):
	IPstringList = split (IPstring, ".")
	if len (IPstringList) != 4:
		return -1
	IPlist = []
	for str in IPstringList:
		try:
			num = int(str)
		except:
			return -1
		if num > 255 or num < 0:
			return -1	
		IPlist = IPlist + [num]
	return IPlist
	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def getNumber (default, prompt, numRange=0):
	while 1:
		r = raw_input (prompt)
		if r== "":
			return default
		try:
			num = int (r)
			if numRange==0:
				return num
			if num in range(numRange):
				return num
			else:
				prompt = "number outside range - try again: "
		except:
			prompt = "invalid input - try again: "		

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
class _Getch:
    """Gets a single character from standard input.  Does not echo to the
screen."""
    def __init__(self):
        try:
            self.impl = _GetchWindows()
        except ImportError:
            self.impl = _GetchUnix()

    def __call__(self): return self.impl()


class _GetchUnix:
    def __init__(self):
        import tty, sys

    def __call__(self):
        import sys, tty, termios
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setraw(sys.stdin.fileno())
            ch = sys.stdin.read(1)
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
        return ch


class _GetchWindows:
    def __init__(self):
        import msvcrt

    def __call__(self):
        import msvcrt
        return msvcrt.getch()


getch = _Getch()
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	








BS = 256

ERR_BEFORE = """

  The error has occured before the data has been written
  This means that you still have a valid configuration,
  albeit the old one
  Please check if your gluion is properly connected, that
  the LED is lit, and that you provided the proper IP number
  and UDPport. Then try again.
  
	"""

ERR_AFTER = """

  the error has ocurred after data has already been written
  DO NOT UNPLUG POWER or you will be left with a partial
  configuration that is corrupt and will neither work
  nor allow further upload attempts.
  Instead try to repeat the upload procedure until it
  succeeds. Otherwise you can only upload thru the serial port

"""




#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def blockZero ():
	block = ''
	for i in range (BS):
		block = block + '\x00'
	return block
	
def blockOne ():
	block = ''
	for i in range (BS):
		block = block + '\xFF'
	return block
	
def blockTest ():
	block = ''
	for i in range (BS):
	#	block = block + chr (i)
		block = block + chr (i / 4)
	return block

		

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def getBlock (si):
	rc=-1
	trials=0
	while rc<0:
		block = ''
		try:
			block, address = si.recvfrom (1024)
		except socket.timeout:
			return -1
	#	print "dump getBlock"
	#	OSC.hexDump (block)
		rc = block.find ("/config")
		trials=trials+1
		if trials>5:
			return -2
	return block[16:]	# strip address, tags, and blob length (assuming we always get back blobs)
		
	
def sendBlock (IPSS, block):
	msg = OSC.OSCMessage()
	msg.setAddress("/config")
	msg.append (block, 'b')
	oscdata = msg.getBinary()
#	OSC.hexDump (oscdata)
	try:
		IPSS["so"].sendto (oscdata, (IPSS["IP"], IPSS["port"]))
	except:
		print ""
		print ""
		print "couldn't route to gluion at %s (port %d)" % (IPSS["IP"], IPSS["port"])
		print "check your Ethernet connection and IP settings"
		print ""
		sys.exit (0)
	
	
def printBlock(block):
	print len (block)
	msg = OSC.OSCMessage()
	msg.setAddress("/config")
	msg.append (block, 'b')
	oscdata = msg.getBinary()
#	OSC.hexDump (oscdata)

	
	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def ping (IPSS):
	# "ping" gluion, we also send some test data
	print "    -> establishing communication with the gluion..."
	print "       .",
	outBlock = blockTest()
	trials = 0
	sendBlock (IPSS, '\xA5\x01\x80\x00' + outBlock)		# A5 = loopback data
	while 1:
		inBlock = getBlock (IPSS["si"])		# ...and see if get the same data back
		if inBlock != -1 and inBlock [4:BS] == outBlock [0:BS-4]:	# data is slightly offset here
			print "done"
			return 0
		else:
		#	print ":",
		#	print "bad ping"
		#	time.sleep (3)
			trials = trials + 1
			if trials >= 200:		# we're doing this quite often because there's a lot of buffered sensor data to throw away
				print "failed (tried %d times)" % trials
			#	OSC.hexDump(inBlock)
				return -1


#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def testErase (IPSS):
	# erase a test sector...
	print "    -> erasing test sector..."
	print "       .",
	trials = 0
	sendBlock (IPSS, '\xE8\x06\x00\x00' + blockZero())	# E8 = erase sector   -   sector 6 page 0 byte 0
	while (1):
		inBlock = getBlock (IPSS["si"])		# ...and we should get back an erased page (all bytes are 0xFF)
	#	OSC.hexDump(inBlock)
		if inBlock == blockOne():
			print "done"
			return 0
		else:
			print ":",
			trials = trials + 1
			if trials >= 5:
				print "failed (tried %d times)" % trials
				print inBlock
				if len(inBlock) == 0:
					print "got no data"
				else:
					OSC.hexDump(inBlock)
				return -1
	

def testWrite (IPSS):
	# write something to the last sector...
	print "    -> writing test data..."
	print "       .",
	outBlock = blockTest()
	sendBlock (IPSS, '\xD4\x06\x00\x00' + outBlock)	# D4 = write page	-	sector 6 page 0 byte 0
	inBlock = getBlock (IPSS["si"])	# ...and see if get the same data back
#	OSC.hexDump(inBlock)
	if inBlock == outBlock:
		print "done"
		return 0
	else:					# we can't retry this operation as we have to erase the sector first
		print "failed"		# instead we have to repeat in an upper loop
		return -1



	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def configErase (IPSS):
	# erase all but last sector...
	# we keep the last sector with whatever runtime parameters we have
	print "    -> erasing configuration sectors..."
	print "       .",
	goodBlocks = 0
	for i in range (6):
		sendBlock (IPSS, '\xE8' + chr(i) + '\x00\x00' + blockZero())	# E8 = erase sector   -   sector i page 0 byte 0
		inBlock = getBlock (IPSS["si"])	# ...and we should get back an erased page (all bytes are 0xFF)
		if inBlock == blockOne():
			goodBlocks += 1
		print ".",
	if goodBlocks == 6:		
		print "done"
		return 0
	else:
		print "failed"
		return -3
	

def configWrite (IPSS, data):
	# now finally writing the actual data
	print "    -> writing configuration data..."
	print "      ",
	blockNum = len (data) / BS
	for i in range (blockNum):
		pageNum = chr (i%BS)
		sectorNum = chr (i/BS)
		address = (sectorNum + pageNum + '\x00')
		outBlock = data [(i*BS):((i+1)*BS)]
	#	printBlock ('\xD4' + address + outBlock)
		sendBlock (IPSS, '\xD4' + address + outBlock)	# D4 = write page
		inBlock = getBlock (IPSS["si"])	# ...and see if get the same data back
	#	OSC.hexDump (inBlock)		
		if inBlock == outBlock:
			if i%32 == 31:
				print ".",
		else:
			sendBlock (IPSS, '\xB2' + address + blockZero())	# maybe it's an error during verification, so try to read again
			inBlock = getBlock (IPSS["si"])						# retrying the write command itself doesn't work because we have to erase a whole sector before writing again
			if inBlock == outBlock:
				if i%16 == 15:
					print ":",
			else:
				print "failed"
				return -1
	print "done"	
	return 0


def configVerify (IPSS, data):
	# verifying data (we already did that for every single page right after each was written, but checking twice doesn't hurt)
	print "    -> verifying configuration data..."
	print "      ",
	blockNum = len (data) / BS
	for i in range (blockNum):
		pageNum = chr (i%BS)
		sectorNum = chr (i/BS)
		address = (sectorNum + pageNum + '\x00')
		sendBlock (IPSS, '\xB2' + address + blockZero())	# B2 = read page
		inBlock = getBlock (IPSS["si"])
	#	OSC.hexDump (inBlock)
		if inBlock == data [(i*BS):((i+1)*BS)]:
			if i%32 == 31:
				print ".",
		else:
			print "failed"
			return -1
	print "done"
	return 0


	
	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#def IPread ():
	
def IPerase (IPSS):
	# erase third sector...
	print "    -> erasing IP settings sectors..."
	print "       .",
#	sendBlock (IPSS, '\xE8\x07\x00\x00' + blockZero())	# E8 = erase sector   -   sector 7 page 0 byte 0
#	inBlock = getBlock (IPSS["si"])	# ...and we should get back an erased page (all bytes are 0xFF)
#	OSC.hexDump(inBlock)
#	if inBlock == blockOne():
#		print "done"
#		return 0
#	else:
#		print "failed"
#		return -1
	trials = 0
	sendBlock (IPSS, '\xE8\x07\x00\x00' + blockZero())	# E8 = erase sector   -   sector 7 page 0 byte 0
	while (1):
		inBlock = getBlock (IPSS["si"])		# ...and we should get back an erased page (all bytes are 0xFF)
	#	OSC.hexDump(inBlock)
		if inBlock == blockOne():
			print "done"
			return 0
		else:
			print ":",
			trials = trials + 1
			if trials >= 5:
				print "failed (tried %d times)" % trials
				print inBlock
				if len(inBlock) == 0:
					print "got no data"
				else:
					OSC.hexDump(inBlock)
				return -1
	
	

def IPwrite (IPSS, data):
	# now finally writing the actual data
	print "    -> writing IP settings..."
	print "       .",
#	printBlock ('\xD4\x07\x00\x00' + data)
	sendBlock (IPSS, '\xD4\x07\x00\x00' + data)		# D4 = write page - sector 7 page 0 byte 0
	inBlock = getBlock (IPSS["si"])	# ...and see if get the same data back
#	OSC.hexDump (inBlock)		
	if inBlock == data:
		print "done"
		return 0
	else:					# we can't retry this operation as we have to erase the sector first
		print "failed"		# instead we have to repeat in an upper loop
		return -1


def IPverify (IPSS, data):
	# verifying data
	print "    -> verifying IP settings..."
	print "       .",
	sendBlock (IPSS, '\xB2\x07\x00\x00' + blockZero())	# B2 = read page
	inBlock = getBlock (IPSS["si"])
#	OSC.hexDump (inBlock)
	if inBlock == data:
		print "done"
		return 0
	else:
		print "failed"
		return -1
	
	



#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def getData (filename):
	try:
		f = open (filename, 'rb')
	except:
		print "could not open file %s" % filename
		return -1
	try:
		data = f.read()
	except:
		print "could not open file %s" % filename
		return -2
	while (len(data) % BS != 0):
		data = data + '\x00'
	return (data)
	



#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def doTest (IPSS):
	# first we need to check if we can access the gluion's configuration PROM
	# to do so we erase an unused sector, write something to it, and see if we can read it back
	# if we don't get anything back we don't attempt further access to the PROM, so as not to risk overwriting
	# a working configuration with something broken
	# the test sector is the last of four - the first two are for the .rbf, the third holds parameters

	#see if we can write to the gluion's configuration PROM
	print "  -> testing access to the gluion's configuration memory"
	trials = 0
	while (1):
		rc = ping (IPSS)
		if rc == 0:
			rc = testErase (IPSS)
			if rc == 0:
				rc = testWrite (IPSS)
			#	sendBlock (IPSS, '\xB2\x00\x00\x00' + blockZero())	# B2 = read page
			#	inBlock = getBlock (si)
			#	OSC.hexDump(inBlock)
				if rc == 0:
					return 0
		trials = trials + 1
		if trials > 3:
			print ""
			print "  -> test failed - giving up"
			return -1
		print ""
		print "  -> test failed - now trying again"




		
		
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	new interactive functions
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def scanPort ():
	print "scanning ports for gluion (not implemented yet)"


	
	

	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def uploadConfig (port, gluionIP):
	# check directory for .rbf files
	# RE reminder: 
	#		.	any character
	#		\.	literal dot
	#		\s	any whitespace
	#		\S	any non-whitespace
	#		\d	digit
	#		+	repeat preceeding once or more
	if os.name=="posix":
		cmdStr = "ls -l *.rbf"
		rbfFile = re.compile ("\S+\.rbf", re.IGNORECASE)
	elif os.name in ("nt", "dos", "ce"):
		cmdStr = "dir *.rbf"
		rbfFile = re.compile ("(?<=\.\d\d\d ).+\.rbf", re.IGNORECASE)	# 
	else:
		print "unknown OS type"	

	dirFile = os.popen (cmdStr)	# use system calls instead of additional libraries
	dirOutput = dirFile.read()
	
	FileObj = rbfFile.search (dirOutput)
	ptr = 0
	FileNum = 0
	FileList = []
	while type(FileObj) is not NoneType:
		File = FileObj.group()
		print File
		ptr = ptr + FileObj.end()
		FileObj = rbfFile.search (dirOutput[ptr:])
		FileList [FileNum:] = [File]
		FileNum=FileNum+1
	if FileNum==0:
		print "no configuration files (*.rbf) found"
		print "put them in the same directory as squark.py"
		raw_input("press return")
		return
	print ""
	print "available configurations:"
	print ""
	for i in range (FileNum):
		print "%2d %s" % (i, FileList[i])
	print ""
	r = getNumber (-1, "enter number of file to upload (press return to cancel): ", FileNum)
	if r==-1:
		return
	FileName = FileList[r]
	print ""
	print "confirm upload of " + FileName + " [y/n]"
	rc = 'q'
	while rc not in ['y', 'Y', 'n', 'N']:
		rc = getch()
	if rc not in ("y", "Y"):
		return
	print ""
	
	# get data from configuration file
	configData = getData (FileName)
	if configData < 0:
		print "bad file data"		
		raw_input("press return")
		return
		
	# information gathered, now upload --------------------------------------------------------
	si = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	si.bind (('', port))
	si.settimeout (3.0)
	so = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	
	IPSS = {"port": port, "IP": gluionIP, "si": si, "so": so}

	# test connection
	rc = doTest (IPSS)
	print ""
	if rc < 0:
		print ERR_BEFORE
		raw_input("press return")
		return
	
	# now that we established succesful contact with the gluion we can write the actual configuration data to its PROM
	print "  -> now uploading new configuration"
	trials = 0
	while (1):
		rc = configErase (IPSS)
		if rc == 0:
			rc = configWrite (IPSS, configData)
			if rc == 0:
				rc = configVerify (IPSS, configData)
				if rc == 0:
					print ""
					print "succesfully uploaded configuration"
 					print "you now must reboot the device"
					print "(i.e. unplug the power connector, then reconnect it)"
					print ""
					break
		trials = trials + 1
		if trials >= 3:
			print ""
			print "  -> upload failed - giving up"
			print ERR_AFTER
			break
		print ""
		print "  -> upload failed - now trying again"	
		
	si.close ()
	so.close ()	
	raw_input("press return")
	
	
	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def getEEPROM (port, gluionIP):
	si = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	si.bind (('', port))
	si.settimeout (3.0)
	so = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	
	IPSS = {"port": port, "IP": gluionIP, "si": si, "so": so}

	sendBlock (IPSS, '\xB2\x07\x00\x00' + blockZero())	# B2 = read page

	inBlock = getBlock (si)
#	OSC.hexDump (inBlock)

	IP0 = str(ord(inBlock[0]))+'.'+str(ord(inBlock[1]))+'.'+str(ord(inBlock[2]))+'.'+str(ord(inBlock[3]))
	IP1 = str(ord(inBlock[4]))+'.'+str(ord(inBlock[5]))+'.'+str(ord(inBlock[6]))+'.'+str(ord(inBlock[7]))
	port0 = ord(inBlock[8]) * 256 + ord(inBlock[9])
	port1 = ord(inBlock[10]) * 256 + ord(inBlock[11])
	serial = {	"exp":	ord(inBlock[12]) >> 4,
				"rev":	ord(inBlock[12]) & 15,
				"sub":	ord(inBlock[13]),
				"ser":	ord(inBlock[14])
			 }
	
	EEPROM = {"gluionIP": IP0, "gluionPort": port0,
			  "hostIP": IP1,   "hostPort": port1,
			  "serial": serial    }
#	print EEPROM		
	
	si.close()
	so.close()
	return EEPROM
		
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def setEEPROM (port, gluionIP, EEPROM):
	outBlock = ''
	IP = splitIP (EEPROM["gluionIP"])
	for i in range (4):
		outBlock = outBlock + chr(IP[i])

	IP = splitIP (EEPROM["hostIP"])
	for i in range (4):
		outBlock = outBlock + chr(IP[i])
	
	eport = EEPROM["gluionPort"]
	outBlock = outBlock + chr (eport >> 8)
	outBlock = outBlock + chr (eport & 255)
	outBlock = outBlock + chr (eport >> 8)
	outBlock = outBlock + chr (eport & 255)
	
	serial = EEPROM["serial"]
	outBlock = outBlock + chr (serial["exp"] * 16 + serial["rev"])
	outBlock = outBlock + chr (serial["sub"])
	outBlock = outBlock + chr (serial["ser"])

	while (len (outBlock) % BS != 0):	# pad data 
		outBlock = outBlock + '\x00'

#	OSC.hexDump (outBlock)

	# block assembled, now upload --------------------------------------------------------
	si = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	si.bind (('', port))
	si.settimeout (3.0)
	so = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	
	IPSS = {"port": port, "IP": gluionIP, "si": si, "so": so}

	# test connection	
	rc = doTest (IPSS)
	if rc < 0:
		print ERR_BEFORE
		sys.exit (0)
			
	# now that we established succesful contact with the gluion we can write the actual configuration data to its PROM
	print "  -> now uploading new IP/port settings"
	trials = 0
	while (1):
		rc = IPerase (IPSS)
		if rc == 0:
			rc = IPwrite (IPSS, outBlock)
			if rc == 0:
				rc = IPverify (IPSS, outBlock)
				if rc == 0:
					print ""
					print "succesfully uploaded IP settings"
					print "reboot the device you now must"
					print "(i.e. unplug the power connector, then reconnect it)"
					print ""
					break
		trials = trials + 1
		if trials >= 3:
			print ""
			print "  -> upload failed - giving up"
			print ERR_AFTER
			break
		print ""
		print "  -> upload failed - now trying again"

	si.close ()
	so.close ()			
	raw_input("press return")
	return rc
	
	
	
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def checkPort (port):	# checks for gluion packets on the current listening port. Returns the IP address and serial number
	cls ()
	print "waiting for gluion message on port %d..." % listeningPort
	IP = 0
	serial = 0
	
	# try binding to port
	si = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	si.bind (('', port))
	si.settimeout (3.0)
	try:
		block, address = si.recvfrom (1024)
	except socket.timeout:
		si.close()
		return (0, 0)
	IP = address[0]
	
	# check received packet for OSC tags
	rc = block.find ("#bundle")
	if rc < 0:
		print "some other device is sending on port %d" % listeningPort
		raw_input("press return to continue")	
		si.close()
		return (0, 0)
	
	# retrieve MAC from ARP table
	cmdStr = "arp -a" # + IP		# only look for an entry related to the found address (might be old - how to fix that? -d * then force renegotiation??)
	arpFile = os.popen (cmdStr)	# use system calls instead of additional libraries
	arpOutput = arpFile.read()
	gluionID = re.compile ("05.E2.87.[\dA-F][\dA-F].[\dA-F][\dA-F].[\dA-F][\dA-F]", re.IGNORECASE)	# the magic gluion key 05.E2.87 followed by the serial numbers
	MACobj = gluionID.search (arpOutput)
	if type(MACobj) is not NoneType:
		MAC = MACobj.group()	
		# extract serial number from second part of MAC
		serial = {	"exp":	int(MAC[9:10],16), 		# expansion for subversion revision
					"rev":	int(MAC[10:11],16),		# board revision (1=A, 2=B, 3=B2)
					"sub":	int(MAC[12:14],16),		# subversion revision
					"ser":	int(MAC[15:17],16)		# serial number
				 }
	si.close ()
	return (IP, serial)



#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def changeIP (currentIP, initialPrompt):
	prompt = initialPrompt
	while 1:
		IP = raw_input (prompt)
		if IP=="":
			return currentIP
		IPcheck = splitIP (IP)
		if IPcheck<0:
			prompt = "invalid IP - try again: "
			continue
		else:
			return IP
		
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def changePort (currentPort):
	prompt = "enter new port number (press return to cancel): "
	while 1:
		r = raw_input(prompt)
		if r== "":
			return currentPort
		try:
			port = int (r)
			if port<0 or port>65535:
				prompt = "invalid port - try again: "
				continue
			if port<1024:
				print ""
				print "watch out, this is a common port that might be reserved for certain services"
				print ""
				raw_input("press return")
			return port
		except:
			prompt = "invalid input - try again: "		

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def changeSerial (currentSerial):
	exp = int (raw_input ("enter new expansion number: "))
	rev = int (raw_input ("enter new board revision number: "))
	sub = int (raw_input ("enter new subversion revision number: "))
	ser = int (raw_input ("enter new serial number: "))
	serial = {"exp": exp, "rev": rev, "sub": sub, "ser": ser}	
	return serial
	


#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	network parameters menu
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def networkMenu (port, IP, serial, EEPROM, newEEPROM):
	cls ()
	print "gluion visible at port %d" % port
	print "               with IP %s" % IP
	if serial==0:
		print "               (serial number not available)"
	else:
		print "            and serial %d.%d.%d.%d" % (serial["exp"], serial["rev"], serial["sub"], serial["ser"])	
	print ""
	print "                    This is stored         These are" 
	print "                     in the gluion      your changes"
	print ""
	if EEPROM["gluionIP"]==newEEPROM["gluionIP"]:
		print "[1] gluion IP:   %16s" % EEPROM["gluionIP"]
	else:
		print "[1] gluion IP:   %16s   %16s" % (EEPROM["gluionIP"], newEEPROM["gluionIP"])
		
	if EEPROM["gluionPort"]==newEEPROM["gluionPort"]:
		print "[2] gluion port: %16d" % EEPROM["gluionPort"]
	else:
		print "[2] gluion port: %16d   %16d" % (EEPROM["gluionPort"], newEEPROM["gluionPort"])
		
	print ""

	if EEPROM["hostIP"]==newEEPROM["hostIP"]:
		print "[3] host IP:     %16s" % EEPROM["hostIP"]
	else:
		print "[3] host IP:     %16s   %16s" % (EEPROM["hostIP"], newEEPROM["hostIP"])
		
	if EEPROM["hostPort"]==newEEPROM["hostPort"]:
		print "[2] host port:   %16d" % EEPROM["hostPort"]
	else:
		print "[2] host port:   %16d   %16d" % (EEPROM["hostPort"], newEEPROM["hostPort"])

	print ""
	
	EEserial = EEPROM["serial"]
	newEEserial = newEEPROM["serial"]
	print "gluion serial:         %d.%d.%d.%d" % (EEserial["exp"], EEserial["rev"], EEserial["sub"], EEserial["ser"])
	if EEserial != newEEserial:
		print "new gluion serial:     %d.%d.%d.%d" % (newEEserial["exp"], newEEserial["rev"], newEEserial["sub"], newEEserial["ser"])	
	
	print ""
	
	if EEPROM["hostIP"]!=newEEPROM["hostIP"]:
		print "Note: you're about to change the IP the gluion transmits to."
		print "  !!! Make sure to change your host's network setting   !!!"
		print "  !!! accordingly after uploading and restart this tool !!!"
		print ""
		
	if EEPROM==newEEPROM and EEserial!=newEEserial:
		print "Note: the fact that the serial stored in your gluion"
		print "  is different from the one reported in the header"
		print "  most likely means that your gluion's configuration"
		print "  has a hardcoded serial."
		print "  This is nothing to worry about."
		print ""
		
	print "[u] upload changes"
	print "[r] return to main menu"
	print "[q] quit"
	print ""	
	


#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	search gluion menu
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def searchMenu ():
	cls ()
	print "no gluion in sight at port %d" % listeningPort
	print ""
	print "[c] check again (make sure you are connected and the gluion is powered)"
	print "                (transmission LED must blink, otherwise reboot gluion)"
	print "[p] change listening port"
#	print "[s] scan ports for gluion"
	print ""
	print "[q] quit"
	print ""
	


#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	main menu
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
def mainMenu (port, IP, serial):
	cls ()
	print "gluion visible at port %d" % port
	print "               with IP %s" % IP
	if serial==0:
		print "               (serial number not available)"
	else:
		print "            and serial %d.%d.%d.%d" % (serial["exp"], serial["rev"], serial["sub"], serial["ser"])	
	print ""
	print "[u] upload new configuration"
	print "[p] change listening port"
	print "[n] change the gluion's network parameters"
	print ""
	print "[q] quit"
	print ""



#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#	main squark state machine
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
if __name__ == "__main__":
	state = "search gluion"
	listeningPort = 57120
	gluionIP = "192.168.5.77"
	gluionSerial = 0
	EEPROMchanged = 0
	

	cls ()
	gluionIP, gluionSerial = checkPort (listeningPort)
	if gluionIP != 0:
		state = "get action"
	
	while state != "quit":
		if state=="search gluion":
			searchMenu ()
		
			rc = ''
			while rc not in ['c', 'p', 's', 'q']:
				rc = getch()
		
			if rc=='c':
				gluionIP, gluionSerial = checkPort (listeningPort)
				if gluionIP!=0:
					state="get action"
			elif rc=='p':
				newPort = changePort (listeningPort)
				if newPort!=listeningPort:
					listeningPort = newPort
					gluionIP, gluionSerial = checkPort (listeningPort)
					if gluionIP!=0:
						state="get action"
		#	elif rc=='s':
		#		scanPort ()
			elif rc=='q':
				state = "quit"

		elif state=="get action":
			mainMenu (listeningPort, gluionIP, gluionSerial)
			rc = ''
			while rc not in ['u', 'p', 'n', 'q']:
				rc = getch()
	
			if rc=='u':
				uploadConfig (listeningPort, gluionIP)
			elif rc=='p':
				listeningPort = changePort (listeningPort)
				gluionIP, gluionSerial = checkPort (listeningPort)
				if gluionIP==0:
					state="search gluion"
			elif rc=='n':
				EEPROM = getEEPROM (listeningPort, gluionIP)
				newEEPROM = EEPROM.copy ()
				state="get network"
			elif rc=='q':
				state = "quit"

		elif state=="get network":
			networkMenu (listeningPort, gluionIP, gluionSerial, EEPROM, newEEPROM)
			rc = ''
			while rc not in ['1', '2', '3', '9', 'u', 'r', 'q']:
				rc = getch()
	
			if rc=='1':
				newEEPROM["gluionIP"] = changeIP (newEEPROM["gluionIP"], "enter new IP for the gluion (press return to cancel): ")
			elif rc=='2':
				newEEPROM["gluionPort"] = newEEPROM["hostPort"] = changePort (newEEPROM["gluionPort"])
			elif rc=='3':
				newEEPROM["hostIP"] = changeIP (newEEPROM["hostIP"], "enter new IP the gluion should transmit to (press return to cancel): ")
			elif rc=='9':
				newEEPROM["serial"] = changeSerial (newEEPROM["serial"])	
			elif rc=='u':
				rcs = setEEPROM (listeningPort, gluionIP, newEEPROM)
				if rcs==0:	# after a succesful upload we can switch to the new listeningPort (might be the same of course if only changed IPs=
					listeningPort = newEEPROM["gluionPort"]
					gluionIP, gluionSerial = checkPort (listeningPort)
					EEPROM = getEEPROM (listeningPort, gluionIP)
					newEEPROM = EEPROM.copy ()
			elif rc=='r':
				state = "get action"
			elif rc=='q':
				state = "quit"
			
	glear ()				
	