package playground.meisterk.org.matsim.run.ptRouting;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.matsim.core.controler.Controler;
import org.matsim.matrices.Matrices;
import org.matsim.matrices.Matrix;
import org.matsim.visum.VisumMatrixReader;
import org.matsim.world.MatsimWorldReader;
import org.matsim.world.World;
import org.xml.sax.SAXException;

import playground.marcel.kti.router.SwissHaltestellen;
import playground.meisterk.org.matsim.run.ktiYear3.KTIControler;


public class PTRoutingInfo {
	private Matrix ptTravelTimes = null;
	private SwissHaltestellen haltestellen = null;
	private World localWorld=null;

	private static final Logger log = Logger.getLogger(PTRoutingInfo.class);
	
	public void prepareKTIRouter(Controler c) {
		
		boolean usePlansCalcRouteKti = Boolean.parseBoolean(
				c.getConfig().getModule(KTIControler.KTI_CONFIG_MODULE_NAME).getValue("usePlansCalcRouteKti"));
		if (!usePlansCalcRouteKti) {
			log.error("The kti module is missing.");
		}

		// municipality layer from world file
		this.localWorld = new World();
		String worldFilename = c.getConfig().getModule(KTIControler.KTI_CONFIG_MODULE_NAME).getValue("worldInputFilename");
		try {
			new MatsimWorldReader(localWorld).parse(worldFilename);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		log.info("Reading traveltime matrix...");
		Matrices matrices = new Matrices();
		this.ptTravelTimes = matrices.createMatrix("pt_traveltime", localWorld.getLayer("municipality"), null);
		VisumMatrixReader reader = new VisumMatrixReader(this.ptTravelTimes);
		reader.readFile(c.getConfig().getModule(KTIControler.KTI_CONFIG_MODULE_NAME).getValue("pt_traveltime_matrix_filename"));
		log.info("Reading traveltime matrix...done.");

		log.info("Reading haltestellen...");
		this.haltestellen = new SwissHaltestellen(c.getNetwork());
		try {
			haltestellen.readFile(c.getConfig().getModule(KTIControler.KTI_CONFIG_MODULE_NAME).getValue("pt_haltestellen_filename"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		log.info("Reading haltestellen...done.");

	}

	public Matrix getPtTravelTimes() {
		return ptTravelTimes;
	}

	public SwissHaltestellen getHaltestellen() {
		return haltestellen;
	}

	public World getLocalWorld() {
		return localWorld;
	}
}
