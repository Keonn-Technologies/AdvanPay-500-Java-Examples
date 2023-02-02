package com.keonn.adpy500.examples;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

	private static final int DEF_READ_POWER 			= 1800;
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
	private static int READ_THRESHOLD = 2;
	private static int READ_WINDOW_MS = 2000;
	
	private static int RESET_WINDOW_MS = 1500;
	
	/* Defines the maximum time a validation can take, if it takes longer, the current deactivation operation is cancelled */
	private static int VALIDATING_WINDOW_MS = 1000;
	
	/* Defines the maximum time a deactivation operation is available, if it takes longer, the current deactivation operation is cancelled */
	private static int DEACTIVATING_WINDOW_MS = 2500;
	
	/* Defines the idle period after a deactivation operation. No more than one deactivation can happen in this time period */
	private static int DEACTIVATION_PERIOD_MS = 2500;
	
	/* Define a delay for the validation process of the epcs */
	private static int VALIDATION_DELAY_MS = 300;
	
	/* Minimum time window  for the tag being undetected (sensor in low state) to allow a transition READ -> VALIDATING*/
	private static int LOW_LEVEL_WINDOW_MS = 1000;
	
	
	/* Read power defined in cdBm */
	private static int readPower;
	
	/* EPCgen2 Session/Target/Q */
	private static Session session = Session.S0;
	private static Target target = Target.A;
	private static Gen2.Q q = new Gen2.StaticQ(2);

	private static int readTime=DEF_READ_TIME_MS;

	private static Region region;
	private static String connectionString;
	private static List<String> deniedEPCs;


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
		parser.addStringOption("x", "denied", "Comma separated list of epcs that will fail the validation", false, false);
		parser.addIntegerOption("z", "power", "Power in cdBm", false, false);
		parser.addIntegerOption("o", "read", "Read time in milli seconds", false, false);
		parser.addIntegerOption("a", "deactivation-window", "Deactivation window time (ms)", false, false);
		parser.addIntegerOption("b", "deactivation-action", "Deactivation action time (ms)", false, false);
		parser.addIntegerOption("d", "validation-windows", "Validation window time (ms)", false, false);
		parser.addIntegerOption("e", "read-windows", "Read window time (ms)", false, false);
		parser.addIntegerOption("f", "read-threshold", "Read threshold (reads)", false, false);
		parser.addIntegerOption("g", "validation-delay", "Validation delay (ms)", false, false);

		String t=null;
		String s=null;
		String r=null;
		int power;
		try {
			parser.parse(args);
			
			power = parser.getIntegerOptionValue("z", DEF_READ_POWER);
			readTime = parser.getIntegerOptionValue("o", DEF_READ_TIME_MS);
			DEACTIVATING_WINDOW_MS = parser.getIntegerOptionValue("a", DEACTIVATING_WINDOW_MS);
			DEACTIVATION_PERIOD_MS = parser.getIntegerOptionValue("b", DEACTIVATION_PERIOD_MS);
			VALIDATING_WINDOW_MS = parser.getIntegerOptionValue("d", VALIDATING_WINDOW_MS);
			READ_WINDOW_MS = parser.getIntegerOptionValue("e", READ_WINDOW_MS);
			READ_THRESHOLD = parser.getIntegerOptionValue("f", READ_THRESHOLD);
			VALIDATION_DELAY_MS = parser.getIntegerOptionValue("g", VALIDATION_DELAY_MS);
			
			
			connectionString = parser.getStringOptionValue("c",DEF_DEVICE);
			t = parser.getStringOptionValue("t",DEF_TARGET.toString());
			String qu = parser.getStringOptionValue("q",""+DEF_Q.initialQ);
			s = parser.getStringOptionValue("s",DEF_SESSION.toString());
			r = parser.getStringOptionValue("r",DEF_REGION.toString());
			String denied = parser.getStringOptionValue("x",null);
			if(denied!=null && denied.length()>0) {
				/* parse list of denied epcs */
				try {
					deniedEPCs = Arrays.asList(denied.toUpperCase().split(",", -1));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

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
			
			app.runWithRetries();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private SerialReader reader;
	private boolean terminate = false;
	
	/* Contains the last deactivation action */
	private long lastDeactivate=0;
	
	/* Map with all detected Tags and its state. WARN: this map will grow unlimited unless some clean up is implemented */
	//private Map<String,TagTracker> tagStates = new HashMap<>();
	
	/* Tag that is currently in the process of being read/validated/deactivated */
	private ActiveTag activeTag=new ActiveTag();
	
	private ExecutorService executor = Executors.newFixedThreadPool(2);
	
	private TagValidator tagValidator = new TagValidator() {
		
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
		@Override
		public boolean validateTag(String epc) {
			try {
				Thread.sleep(VALIDATION_DELAY_MS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(deniedEPCs!=null) {
				return !deniedEPCs.contains(epc.toUpperCase());
			}
			return true;
		}
	};
	
	
	
	public ADPY500_Example1() {
	}
	
	/**
	 * Release resources
	 */
	private void shutdown() {
		terminate=true;
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(reader!=null){
			System.out.println("Stopping AdvanPay-500...");
			reader.stopReading();
			reader.destroy();
			reader=null;
		}
	}
	
	private void runWithRetries() {
		try {
			
			
			while(true) {
				run();
			}
			
		} catch (InterruptedException e) {
			System.out.println("InterruptedException caught. Terminating app...");
			System.exit(3);
		} catch (Exception e) {
			e.printStackTrace();
			if(reader!=null) {
				try {
					reader.destroy();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
			activeTag.reset();
			/* wait for some time before retry connection*/
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				System.out.println("InterruptedException caught. Terminating app...");
				System.exit(3);
			}
		}
	}
	
	private void run() throws ReaderException, InterruptedException {
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
		System.out.println();
		
		
		System.out.println("Idle time between processes (ms): "+RESET_WINDOW_MS);
		System.out.println("Read threshold(reads): "+READ_THRESHOLD);
		System.out.println("Read window time (ms): "+READ_WINDOW_MS);
		System.out.println("Validation simulated delay (ms): "+VALIDATION_DELAY_MS);
		System.out.println("Validation maximum time (ms): "+VALIDATING_WINDOW_MS);
		System.out.println("Pre-validation sensor undetection time (ms): "+LOW_LEVEL_WINDOW_MS);
		System.out.println("Deactivation maximum time (ms): "+DEACTIVATING_WINDOW_MS);
		System.out.println("Deactivation idle time after a detachment action (ms): "+DEACTIVATION_PERIOD_MS);
		System.out.println();
		
		
		SimpleReadPlan srp = new SimpleReadPlan(new int[] {1}, TagProtocol.GEN2, null, 100, false);
		reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, srp);
		System.out.println();
		
		boolean oldTagInPlace = false;
		boolean tagInPlace=false;
		long tagInPlaceStartTs=System.nanoTime();
		
		/* main loop that reads continuously and update the activeTag and its state */
		while(!terminate ) {
			
			try {
				
				TagReadData[] tags = reader.read(readTime);
				
				if(tags.length>1) {
					/* Two tags detected */
					/* Reset state and continue reading */
					System.out.println("["+System.currentTimeMillis()+"] More than one tag read. Reset");
					activeTag.reset();
					continue;
				}
				
				/* current time in nanos */
				final long now = System.nanoTime();
				tagInPlace = isTagInPlace(reader);
				if(tagInPlace!=oldTagInPlace) {
					System.out.println("["+System.currentTimeMillis()+"] "+(tagInPlace?"Tag in position":"Tag leaving position"));
					tagInPlaceStartTs=now;
				}
				
				if(activeTag.state == TagState.VALIDATING || activeTag.state == TagState.DEACTIVATED) {
					/* Detect disconnect events and reset, this means possible tampering with when the tag is in the validation or deactivation phase*/
					if(oldTagInPlace && !tagInPlace) {
						System.out.println("["+System.currentTimeMillis()+"] Tag leaving position. Reset system");
						activeTag.reset();
						continue;
					}
				}
				
				if(tags.length==1) {
					/* Tag EPC value */
					String epc = tags[0].epcString();
					
					/* process read */
					activeTag.processRead(epc,tagInPlace,tagInPlaceStartTs);
				} else {
					activeTag.processRead(null,tagInPlace,tagInPlaceStartTs);
				}
				
				if(activeTag.isDeactivationNeeded()) {
					GpioPin[] ins = reader.gpiGet();
					for(GpioPin in: ins) {
						if(in.id==3 && in.high && (now-lastDeactivate)>TimeUnit.MILLISECONDS.toNanos(DEACTIVATION_PERIOD_MS)) {
							// deactivate tag
							System.out.println("["+System.currentTimeMillis()+"] Start deactivate operation.");
							reader.gpoSet(new Reader.GpioPin[] {new Reader.GpioPin(2, true)});
							Thread.sleep(20);
							reader.gpoSet(new Reader.GpioPin[] {new Reader.GpioPin(2, false)});
							lastDeactivate= now;
						}
					}
				}
				
			} finally {
				oldTagInPlace=tagInPlace;
			}
		}
	}

	private boolean isTagInPlace(SerialReader reader2) throws ReaderException {
		GpioPin[] ins = reader.gpiGet();
		for(GpioPin in: ins) {
			if(in.id==3 && in.high) {
				return true;
			}
		}
		return false;
	}

	private Future<Boolean> validationFuture = null;
	private void validation(final ActiveTag tag) {
		
		if(validationFuture!=null) {
			validationFuture.cancel(false);
		}
		
		validationFuture = executor.submit(() -> {
			
			if(tagValidator==null) {
				System.out.println("Tag Validator not defined.");
				System.exit(4);
			}
			
			boolean validated=false;
			try {
				validated = tagValidator.validateTag(tag.getEPC());
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			
			synchronized (activeTag) {
				/* Ignore in case the active epc is now different */
				if(tag.getEPC().equals(activeTag.getEPC())) {
					tag.validate(validated);
				}
			}
			
			return true;
        });
	}

	@Override
	public void tagReadException(Reader r, ReaderException re) {
		re.printStackTrace();
	}
	
	interface TagValidator{
		
		boolean validateTag(String epc);
	}
	
	/**
	 * TagState enum
	 * It is used to identify the state of each EPC being tracked by the system
	 * 
	 */
	enum TagState{
		RESET,			/* Initial state */
						/*
						 Conditions to move to READ state:
						   > At least RESET_WINDOW_MS must elapse in RESET state to allow a transition
						 */
		
		READ,			/* State where the active tag is being read*/
						/* 
						The conditions to enter VALIDATING state are:
						  > Within READ_WINDOW_MS, the tags has been read at least READ_THRESHOLD
						  > the tag sensor has been at least LOW_LEVEL_WINDOW_MS without detecting a tag
						*/
		
		VALIDATING,		/* State where the tag is being analyzed to confirm a deactivation is accepted */
						/* 
						In the validation phase, a 3rd party system confirms whether it must be deactivated.
						
						The conditions to enter DEACTIVATED state are:
						  > The validation completes within the allowed time (DEACTIVATING_WINDOW_MS) and is positive
						  > No reads with a different EPC happens during VALIDATING phase
		 				*/
		
		DEACTIVATED		/* tag is deactivated whenever is detected in its position.*/
						/*
						 The conditions to enter RESET state are:
						   > A different tag is read
						   > Time period DEACTIVATING_WINDOW_MS elapses
						 
						 */
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
		TagState state = TagState.RESET;

		public synchronized void reset() {
			epc=null;
			readsInReadState=0;
			state=TagState.RESET;
			stateStartTs=System.nanoTime();
		}

		public synchronized void read() {
			++readsInReadState;
		}
		
		public String getEPC() {
			return epc;
		}

		public synchronized boolean updateReadThreshold() {
			long now = System.nanoTime();
			boolean reached = readsInReadState>=READ_THRESHOLD && (now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(READ_WINDOW_MS);
			
			/* check if we have surpassed the time threshold */
			/* In such case, reset the state start time, otherwise we would never reach the threshold conditions */
			if((now-stateStartTs)>TimeUnit.MILLISECONDS.toNanos(READ_WINDOW_MS)) {
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
					System.out.println("["+System.currentTimeMillis()+"]["+epc+"] VALIDATING->DEACTIVATED. Tag needs to be In Place.");
				} else {
					reset();
					System.out.println("["+System.currentTimeMillis()+"]["+epc+"] VALIDATING took too long. Reset.");
				}
				
			} else {
				System.out.println("["+System.currentTimeMillis()+"]["+epc+"] VALIDATING failed");
				reset();
			}
		}

		public synchronized boolean isDeactivationNeeded() {
			
			if(state == TagState.DEACTIVATED) {
				long now = System.nanoTime();
				boolean inTime = (now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(DEACTIVATING_WINDOW_MS);
				if(!inTime) {
					System.out.println("["+System.currentTimeMillis()+"]["+epc+"] Closing DEACTIVATION window. Reset");
					reset();
					return false;
				}
				
				return true;
			}
			
			return false;
		}

		public synchronized void processRead(String epc, boolean isTagInPlace, long tagInPlaceStartTs) {
			
			if(epc==null) {
				
				/* update state without a read */
				if(state==TagState.VALIDATING) {
					final long now = System.nanoTime();
					
					if((now-stateStartTs)>TimeUnit.MILLISECONDS.toNanos(VALIDATING_WINDOW_MS)) {
						System.out.println("["+System.currentTimeMillis()+"] validation is taking too long. Reset system.");
						reset();
						return;
					}
				}
				
				return;
			}
			
			if(state==TagState.RESET) {
				final long now = System.nanoTime();
				
				/* Avoid moving to READ if RESET_WINDOW_MS has not elapsed */
				if((now-stateStartTs)<TimeUnit.MILLISECONDS.toNanos(RESET_WINDOW_MS)) {
					return;
				}
			}
			
			if(this.epc==null) {
				this.epc=epc;
				//System.out.println("["+System.currentTimeMillis()+"]["+epc+"] READ[1]");
				state = TagState.READ;
			} else if (!this.epc.equals(epc)) {
				System.out.println("["+System.currentTimeMillis()+"]["+epc+"] first READ. Previous EPC discarded: "+this.epc);
				reset();
				this.epc=epc;
				state = TagState.READ;
			}
			
			if(state == TagState.READ) {
				
				/* In case the tag is in place, do not take into account reads */
				if (isTagInPlace) {
					/* ignore reads while there is a tag in place */
					return;
				}
				
				/* Update the read count */
				read();
				System.out.println("["+System.currentTimeMillis()+"]["+epc+"] READ["+readsInReadState+"]");
				
				/* check if threshold is met */
				if(updateReadThreshold()) {
					
					long now = System.nanoTime();
					if((now-tagInPlaceStartTs)<TimeUnit.MILLISECONDS.toNanos(LOW_LEVEL_WINDOW_MS)) {
						System.out.println("["+System.currentTimeMillis()+"] Low level too small when switching to VALIDATION. Reset system");
						reset();
						return;
					}
					
					/* enter validation phase */
					System.out.println("["+System.currentTimeMillis()+"]["+epc+"] READ->VALIDATING");
					setState(TagState.VALIDATING);
					
					/* request tag validation */
					validation(activeTag);
					
					return;
				}
			}
		}
	}
}
