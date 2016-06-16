/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.dziemke.utils;

import java.util.Collection;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * @author dziemke
 */
public class CreateFacilitiiesFileFromShapeFile {
	private final static Logger LOG = Logger.getLogger(CreateFacilitiiesFileFromShapeFile.class);

	static String shapeFileName = "../../../shared-svn/projects/maxess/data/nairobi/land_use/nairobi_LU_2010/nairobi_LU.shp";
	static String captionOfIdentifierAttribute = "OBJECTID";
	static String captionOfValueAttribute = "LANDUSE";
	static String facilitiesOutputFile = "../../../shared-svn/projects/maxess/data/nairobi/land_use/nairobi_LU_2010/facilites.xml";
	static String facilitiesFileDescription = "Facilities in Nairobi based on Land-Use Shapefile";
	// TODO A coordinate transformation might be necessary
	
	public static void main(String[] args) {
		Collection<SimpleFeature> blocks = collectFeatures();		
		ActivityFacilities activityFacilities = createFacilities(blocks);
		writeFacilitiesFile(activityFacilities);
	}

	
	private static Collection<SimpleFeature> collectFeatures() {
		ShapeFileReader reader = new ShapeFileReader();
		Collection<SimpleFeature> features = reader.readFileAndInitialize(shapeFileName);
		LOG.info("All features collected.");
		return features;
	}
	
	
	private static ActivityFacilities createFacilities(Collection<SimpleFeature> blocks) {
		ActivityFacilities activityFacilities = FacilitiesUtils.createActivityFacilities(facilitiesFileDescription);
		ActivityFacilitiesFactory activityFacilitiesFactory = new ActivityFacilitiesFactoryImpl();
		for (SimpleFeature feature : blocks) {
			String objectId = String.valueOf(feature.getAttribute(captionOfIdentifierAttribute));
			Id<ActivityFacility> id = Id.create(objectId , ActivityFacility.class);
			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			Point point = geometry.getCentroid();
			Coord coord = CoordUtils.createCoord(point.getX(), point.getY());
			ActivityFacility activityFacility = activityFacilitiesFactory.createActivityFacility(id, coord);
			String landUseType = (String) feature.getAttribute(captionOfValueAttribute);
			ActivityOption activityOption = activityFacilitiesFactory.createActivityOption(landUseType);
			activityFacility.addActivityOption(activityOption);
			activityFacilities.addActivityFacility(activityFacility);
		}
		LOG.info("All activity facilities created.");
		return activityFacilities;
	}
	
	
	private static void writeFacilitiesFile(ActivityFacilities activityFacilities) {
		FacilitiesWriter facilitiesWriter = new FacilitiesWriter(activityFacilities);
		facilitiesWriter.write(facilitiesOutputFile);
		LOG.info("Faciltiy file written.");
	}
}