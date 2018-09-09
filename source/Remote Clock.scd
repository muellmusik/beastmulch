/// in REAPER or BM System we need to adjust this to send time code to this machines address and port
/// port can be checked with NetAddr.langPort, but is probably 51720

var bigClock, bigText;
bigClock = Window.new("Big Clock", Rect(600, 800, 800, 180)).alwaysOnTop_(true);
bigClock.alpha = 0.95;
bigClock.onClose = { bigClock = nil; };
bigText = StaticText.new(bigClock, Rect(0, 0, 800, 180)).resize_(5);
bigText.string = "00:00:00.0";
bigText.background = Color.black;
bigText.font = Font("Helvetica-Bold", 200);
bigText.stringColor = Color.new255(106, 90, 205);
bigText.align = \center;
bigClock.front;
bigClock.fullScreen;

OSCFunc({|msg| {bigText.string = msg[1].getTimeString}.defer }, '/time')