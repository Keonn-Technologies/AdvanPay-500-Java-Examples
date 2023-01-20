# AdvanPay-500-Java-Examples

&#8658; Please check our wiki: [https://wiki.keonn.com/rfid-systems/payment-systems/advanpay-500](https://wiki.keonn.com/rfid-systems/payment-systems/advanpay-500) for more information on AdvanPay-500 Conditional RFID UHF Hard Tag Detacher

These examples use a combination of ThingMagic's Java SDK and propietary Keonn libraries to show how to implement some functionalities with AdvanReader-10 series devices.

* **ADPY500_Example1:** Complete example on hoe to operate AdvanPay-500

## How to run the examples:

Run the appropiate command line command listed below from the example's root folder.

### Before starting please discover what port is created when connecting the reader to the usb port as the values listed here are the default ones

In linux is the default port is the created when no other usb device is connected to the system.

#### Windows

**x86 32/64 bits**
```PowerShell
java -classpath bin;lib\slf4j-api-1.6.1.jar;lib\slf4j-simple-1.6.1.jar;lib\mercuryapi-1.35.1.58.jar;lib\JCLAP-1.1.jar com.keonn.adpy500.examples.ADPY500_Example1 -c eapi://COM10
```

#### Linux

**x86 (32/64 bits)**
**ARM (32/64 bits)**
```sh
java -classpath bin:lib/slf4j-api-1.6.1.jar:lib/slf4j-simple-1.6.1.jar:lib/keonn-util.jar:lib/keonn-adrd.jar -Djava.library.path=./native-lib/linux-amd64 com.keonn.adpy500.examples.ADPY500_Example1 -c eapi:///dev/ttyUSB0
```


#### MacOS X (UNTESTED)
```sh
java -classpath bin:lib/slf4j-api-1.6.1.jar:lib/slf4j-simple-1.6.1.jar:lib/mercuryapi-1.35.1.58.jar:lib/JCLAP-1.1.jar com.keonn.adpy500.examples.ADPY500_Example1 -c eapi:///dev/tty.usbserial-A5U2GDO
```
