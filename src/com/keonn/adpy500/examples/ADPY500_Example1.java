package com.keonn.adpy500.examples;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.thingmagic.Gen2;
import com.thingmagic.Gen2.Session;
import com.thingmagic.Gen2.Target;
import com.thingmagic.ReadExceptionListener;
import com.thingmagic.Reader;
import com.thingmagic.Reader.GpioPin;
import com.thingmagic.Reader.Region;
import com.thingmagic.ReaderException;
import com.thingmagic.SerialReader;
import com.thingmagic.SimpleReadPlan;
import com.thingmagic.TMConstants;
import com.thingmagic.TagProtocol;
import com.thingmagic.TagReadData;

import snaq.util.jclap.CLAParser;
import snaq.util.jclap.OptionException;

/**
 *
 * Copyright (c) 2023 Keonn Technologies S.L.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author avives
 *
 */
public class ADPY500_Example1 implements ReadExceptionListener{

	
	private static final int DEF_READ_POWER 			= 1250;
	private static final int DEF_READ_TIME_MS 			= 50;
	private static final String DEF_DEVICE 				= "eapi:///dev/ttyUSB0";
	private static final Gen2.Session DEF_SESSION 		= Gen2.Session.S0;
	private static final Gen2.Target DEF_TARGET 		= Gen2.Target.A;
	private static final Gen2.StaticQ DEF_Q			 	= new Gen2.StaticQ(3);
	private static final Reader.Region DEF_REGION	 	= Reader.Region.EU3;
	
	
	/*
	 * Reads and time threshold.
	 * When the number of reads is reached in the defined period, the tag state goes from READ to VALIDATING
	 */
	private static final int READS_THRESHOLD = 3;
	private static final int READS_THRESHOLD_PERIOD_MS = 2000;
	
	/* Defines the maximum time a validation can take, if it takes longer, the current deactivation operation is cancelled */
	private static final int VALIDATING_WINDOW_MS = 3000;
	
	/* Defines the maximum time a deactivation operation is available, if it takes longer, the current deactivation operation is cancelled */
	private static final int DEACTIVATING_WINDOW_MS = 10000;
	
	/* Defines the idle period after a deactivation operation. No more than one deactivation can happen in this time period */
	private static final long DEACTIVATION_PERIOD_MS = 3000;
	
	/* Read power defined in cdBm */
	private static int readPower;
	
	/* EPCgen2 Session/Target/Q */
	private static Session session = Session.S0;
	private static Target target = Target.A;
	private static Gen2.Q q = new Gen2.StaticQ(3);

	private static int readTime=50;

	private static Region region;
	private static String connectionString;


	/**
	 * main()
	 * It basically handles the program options and then instantiates and executes the AdvanPay-500 example code
	 * @param args
	 */
	public static void main(String[] args){
				
		CLAParser parser = new CLAParser();
		
		parser.addStringOption("c", "connection", "Connection string", true, false);
		parser.addStringOption("t", "target", "EPCGen2 target", false, false);
		parser.addStringOption("q", "qu", "EPCGen2 q", false, false);
		parser.addStringOption("s", "session", "EPCGen2 session", false, false);
		parser.addStringOption("r", "region", "EPCGent2 region", false, false);
		parser.addIntegerOption("z", "power", "Power in cdBm", false, false);
		parser.addIntegerOption("o", "read", "Read time in milli seconds", false, false);

		String t=null;
		String s=null;
		String r=null;
		int power;
		try {
			parser.parse(args);
			
			power = parser.getIntegerOptionValue("z", DEF_READ_POWER);
			readTime = parser.getIntegerOptionValue("o", DEF_READ_TIME_MS);
			connectionString = parser.getStringOptionValue("c",DEF_DEVICE);
			t = parser.getStringOptionValue("t",DEF_TARGET.toString());
			String qu = parser.getStringOptionValue("q",""+DEF_Q.initialQ);
			s = parser.getStringOptionValue("s",DEF_SESSION.toString());
			r = parser.getStringOptionValue("r",DEF_REGION.toString());

			try {
				target = Gen2.Target.valueOf(t);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
				target=DEF_TARGET;
			}
			
			try {
				
				if("dynamic".equalsIgnoreCase(qu)){
					q = new Gen2.DynamicQ();
				} else {
					q = new Gen2.StaticQ(Integer.parseInt(qu));	
				}
				
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
				q=new Gen2.StaticQ(4);	
			}
			
			try {
				session = Gen2.Session.valueOf(s);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
				session=DEF_SESSION;
			}
			
			try {
				region = Reader.Region.valueOf(r);
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
				region = DEF_REGION;
			}
			
			if(power>500 && power<2700){
				readPower=power;
			}
			
		} catch (OptionException e) {
			e.printStackTrace();
			parser.printUsage(System.out, true);
			return;
		}
		
		try {
			
			/**
			 * Shutdown hook to allow resource cleaning
			 */
			final ADPY500_Example1 app = new ADPY500_Example1();
			Runtime.getRuntime().addShutdownHook(new Thread(){
				public void run(){
					app.shutdown();
				}
			});
			
			app.run();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private SerialReader reader;
	
	public ADPY500_Example1() {
	}
	
	/**
	 * Release resources
	 */
	private void shutdown() {
		if(reader!=null){
			System.out.println("Stopping AdvanPay-500...");
			reader.stopReading();
			reader.destroy();
			reader=null;
		}
	}
	
	/* Contains the last deactivation action */
	private long lastDeactivate=0;
	
	/* Map with all detected Tags and its state. WARN: this map will grow unlimited unless some clean up is implemented */
	//private Map<String,TagTracker> tagStates = new HashMap<>();
	
	/* Tag that is currently in the process of being read/validated/deactivated */
	private ActiveTag activeTag=new ActiveTag();
	
	private void run() {
		try {
			
			reader = (SerialReader) Reader.create(connectionString);
			reader.connect();
			
			/* Reader FW/HW */
			String fw = (String) reader.paramGet(TMConstants.TMR_PARAM_VERSION_SOFTWARE);
			String model = (String) reader.paramGet(TMConstants.TMR_PARAM_VERSION_MODEL);
			String serial= (String) reader.paramGet(TMConstants.TMR_PARAM_VERSION_SERIAL);
			System.out.println("RFID model: "+model);
			System.out.println("RFID serial: "+serial);
			System.out.println("RFID software version: "+fw);

			/* reader RFID configuration */
			reader.paramSet(TMConstants.TMR_PARAM_REGION_ID, region);
			reader.paramSet(TMConstants.TMR_PARAM_TAGOP_PROTOCOL, TagProtocol.GEN2);
			reader.paramSet(TMConstants.TMR_PARAM_RADIO_READPOWER, readPower);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, session);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARGET, target);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARI, Gen2.Tari.TARI_25US);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_BLF, Gen2.LinkFrequency.LINK250KHZ);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, q);
			reader.paramSet(TMConstants.TMR_PARAM_GEN2_TAGENCODING, Gen2.TagEncoding.M4);
			reader.addReadExceptionListener(this);
			
			/* Reader GPIO configuration */
			Object obj = reader.paramGet(TMConstants.TMR_PARAM_GPIO_INPUTLIST);
			reader.paramSet(TMConstants.TMR_PARAM_GPIO_INPUTLIST, new int[] {3,4});
			reader.paramSet(TMConstants.TMR_PARAM_GPIO_OUTPUTLIST, new int[] {1,2});
			
			System.out.println("Power: "+reader.paramGet(TMConstants.TMR_PARAM_RADIO_READPOWER));
			System.out.println("region: "+reader.paramGet(TMConstants.TMR_PARAM_REGION_ID));
			System.out.println("session: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_SESSION));
			System.out.println("q: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_Q));
			System.out.println("target: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_TARGET));
			System.out.println("encoding: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_TAGENCODING));
			System.out.println("BLF: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_BLF));
			System.out.println("tari: "+reader.paramGet(TMConstants.TMR_PARAM_GEN2_TARI));
			System.out.println("asyncOnTime: "+reader.paramGet(TMConstants.TMR_PARAM_READ_ASYNCONTIME));
			System.out.println("asyncOffTime: "+reader.paramGet(TMConstants.TMR_PARAM_READ_ASYNCOFFTIME));
			
			SimpleReadPlan srp = new SimpleReadPlan(new int[] {1}, TagProtocol.GEN2, null, 100, false);
			reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, srp);
			System.out.println();
			
			/* main loop that reads continuously and update the activeTag and its state */
			while(true) {
				
				TagReadData[] tags = reader.read(readTime);
				
				if(tags.length>1) {
					/* Two tags detected */
					/* Reset state and continue reading */
					System.out.println("More than one tag read. Reset");
					activeTag.reset();
					continue;
				}
				
				/* current time in nanos */
				long now = System.nanoTime();
				
				if(activeTag.isDeactivationNeeded()) {
					GpioPin[] ins = reader.gpiGet();
					for(GpioPin in: ins) {
						if(in.id==3 && in.high && (now-lastDeactivate)>TimeUnit.MILLISECONDS.toNanos(DEACTIVATION_PERIOD_MS)) {
							// deactivate tag
							System.out.println("Deactivate");
							reader.gpoSet(new Reader.GpioPin[] {new Reader.GpioPin(2, true)});
							Thread.sleep(50);
							reader.gpoSet(new Reader.GpioPin[] {new Reader.GpioPin(2, false)});
							lastDeactivate= now;
						}
					}
				}
				
				if(tags.length==1) {
					/* Tag EPC value */
					String epc = tags[0].epcString();
					
					/* process read */
					activeTag.processRead(epc);
				}
			}
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private Timer timer = new Timer(true);
	private void validation(final ActiveTag tag) {
		
		timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				tag.validate(isEPCAccepted(tag.getEPC()));
			}

		}, 0);
		
	}

	/**
	 * This is where the validation logic goes:
	 * - database access
	 * - http request
	 * - etc
	 * This method is already running asynchronously from the RFID tasks
	 * 
	 * @param epc
	 * @return
	 */
	private boolean isEPCAccepted(String epc) {
		//return epc!=null && !epc.startsWith("33");
		return true;
	}


	@Override
	public void tagReadException(Reader r, ReaderException re) {
		re.printStackTrace();
	}
	
	/**
	 * TagState enum
	 * It is used to identify the state of each EPC being tracked by the system
	 * 
	 */
	enum TagState{
		READ,			/* initial state, the first time a TAG is read */
						/* 
						When the tag has been read enough to enter the validation phase, were a 3rd party system confirms whether it must be deactivated.
						During validation the system keeps reading and stops the process if:
						  > A different tag is read
						  > The validation is not positive
						  > The tag is not detected in place after some time
						*/
		VALIDATING,		/* State where the tag is being analyzed to confirm a deactivation is accepted */
						/* 
						The validation process may end up in three different ways:
						  > The validation process takes longer than a certain defined time, the process is aborted
						  > The validation completes within the allowed time and is positive -> deactivation starts
						  > The validation completes within the allowed time and is negative -> deactivation is not granted
						If the process is positive, the state changes to DEACTIVATED, otherwise it changes to READ to allow a new process take place
		 				*/
		DEACTIVATED		/* tag is deactivated */
	}
	
	/**
	 * ActiveTag class
	 * It holds the active EPC being tracked and all the logic of changing states
	 *
	 */
	private class ActiveTag{
		/* do not access directly */
		String epc=null;
		long stateStartTs;
		int readsInReadState=0;
		TagState state;

		public synchronized void reset() {
			epc=null;
			readsInReadState=0;
			state=null;
			stateStartTs=0;
		}

		public synchronized void read() {
			++readsInReadState;
		}
		
		public String getEPC() {
			return epc;
		}

		public synchronized boolean updateReadThreshold() {
			long now = System.nanoTime();
			boolean reached = readsInReadState>=READS_THRESHOLD && (now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(READS_THRESHOLD_PERIOD_MS);
			
			/* check if we have surpassed the time threshold */
			/* In such case, reset the state start time, otherwise we would never reach the threshold conditions */
			if((now-stateStartTs)>TimeUnit.MILLISECONDS.toNanos(READS_THRESHOLD_PERIOD_MS)) {
				stateStartTs=now;
			}
			
			return reached;
		}

		public synchronized void setState(TagState validating) {
			state = TagState.VALIDATING;
			stateStartTs = System.nanoTime();
		}

		public synchronized void validate(boolean b) {
			if(b) {
				
				long now = System.nanoTime();
				boolean inTime = (now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(VALIDATING_WINDOW_MS);
				if(inTime) {
					state = TagState.DEACTIVATED;
					stateStartTs=now;
					System.out.println("["+epc+"] VALIDATING->DEACTIVATED. Tag needs to be In Place.");
				} else {
					reset();
					System.out.println("["+epc+"] VALIDATING took too long. Reset.");
				}
				
			} else {
				System.out.println("["+epc+"] VALIDATING failed");
				reset();
			}
		}

		public synchronized boolean isDeactivationNeeded() {
			
			if(state == TagState.DEACTIVATED) {
				long now = System.nanoTime();
				boolean inTime = (now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(DEACTIVATING_WINDOW_MS);
				if(!inTime) {
					System.out.println("["+epc+"] Closing DEACTIVATION window. Reset");
					reset();
					return false;
				}
				
				return true;
			}
			
			return false;
		}

		public synchronized void processRead(String epc) {
			
			if(epc==null) {
				return;
			}
			
			if(this.epc==null) {
				this.epc=epc;
				System.out.println("["+epc+"] first READ");
				state = TagState.READ;
			} else if (!this.epc.equals(epc)) {
				System.out.println("["+epc+"] first READ. Previous EPC discarded: "+this.epc);
				reset();
				this.epc=epc;
				state = TagState.READ;
			}
			
			if(state == TagState.READ) {
				
				/* Update the read count */
				read();
				
				/* check if threshold is met */
				if(updateReadThreshold()) {
					/* enter validation phase */
					System.out.println("["+epc+"] READ->VALIDATING");
					setState(TagState.VALIDATING);
					
					/* request tag validation */
					validation(activeTag);
					
					return;
				}
			}
		}
	}
}
