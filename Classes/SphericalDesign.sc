/*
	Copyright the ATK Community and Joseph Anderson, 2011-2017
        J Anderson	j.anderson[at]ambisonictoolkit.net
        M McCrea    mtm5[at]uw.edu

	This file is part of a Spherical Design library for SuperCollider3.
	This is free software:
	you can redistribute it and/or modify it under the terms of the GNU General
	Public License as published by the Free Software Foundation, either version 3
	of the License, or (at your option) any later version.

	The Spherical Design library is distributed in
	the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
	implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
	the GNU General Public License for more details.

	You should have received a copy of the GNU General Public License along with
	this library. If not, see <http://www.gnu.org/licenses/>.
*/


//-------------------------------------------------------------------------
// Third Party Notices
//-------------------------------------------------------------------------
//
//-------------------------------------------------------------------------
// This library includes an extension, found in extSphDesign.sc, containing
// methods which ar a modification of Scott Wilson's SC port of Ville
// Pukki's VBAP in PD. Wilson's comments and Pulkki's copyright statement
// can be found in that file.
//-------------------------------------------------------------------------
//
//-------------------------------------------------------------------------
// The T-Designs found here are from the work of:
//    McLaren's Improved Snub Cube and Other New Spherical Designs in Three
//    Dimensions, R. H. Hardin and N. J. A. Sloane, Discrete and Computational
//    Geometry, 15 (1996), pp. 429-441.
// and are downloaded directly from their site:
//    http://neilsloane.com/sphdesigns/
//-------------------------------------------------------------------------


SphericalDesign {
	var <points; // points are Cartesians
	var initPoints;
	var <design;
	var <view;
	var <triplets; // methods for calculating triplets found in extSphDesigns
	var <>minSideLength = 0.01; // used in calcTriplets

	*new {
		^super.new
	}

	// support for creating a TDesign via SphDesign
	*newT { |nPnts, t|
		^super.new.initTDesign(nPnts, t)
	}

	initTDesign { |nPnts, t|
		design = TDesign(nPnts, t, 3);
	}

	rotate { |angle| this.prUpdateDesign(\rotate, angle) }
	tilt   { |angle| this.prUpdateDesign(\tilt, angle) }
	tumble { |angle| this.prUpdateDesign(\tumble, angle) }

	// modify the design by performing method on all points
	prUpdateDesign { |method ...args|
		this.points_(points.collect(_.perform(method, *args)));
	}

	findAngles { |theta = 0, phi = 0|
		^points.collect{arg point; this.vec_angle(Spherical(1, theta, phi), point)}
	}

	nearestAngle { |theta = 0, phi = 0|
		^this.findAngles(theta, phi).minItem
	}

	nearestIndex { |theta = 0, phi = 0|
		^this.findAngles(theta, phi).minIndex
	}

	nearestPoint { |theta = 0, phi = 0|
		^points[this.nearestIndex(theta, phi)]
	}

	oppositeIndex { |point|
		^this.nearestIndex((point.theta + pi).mod(2pi), point.phi.neg)
	}

	oppositePoint { |point|
		^points[this.oppositeIndex(point)]
	}

	oppositePairsIndices {
		var results, opposites;
		opposites = Array.new;

		points.do{|point, index|
			opposites = opposites.add([index, this.oppositeIndex(point)])
		};

		opposites.postln;

		results = Array.new;

		opposites.do({ |item|
			var test = true;
			results.do({ |result|
				((result == item) or: (result == item.reverse)).if({
					test = false;
				})
			});
			test.if({results = results.add(item)});
		});

		^results.sort({|a, b| a[0] < b[0]})
	}

	resetOrientation { |theta = 0, phi = 0, orientation = 'point'|
		var nearestTheta, nearestPhi;
		case
		{orientation == 'point'} {
			#nearestTheta, nearestPhi = this.nearestPoint(theta, phi).angles;
			this.rotate(nearestTheta.neg);
			this.tumble(nearestPhi.neg);
			this.tumble(phi);
			this.rotate(theta);
		}
	}

	nearestIndices { |theta = 0, phi = 0, spread = 0.5pi, inclusive = true|
		var lessThan;
		inclusive.if({lessThan = '<='}, {lessThan = '<'});
		^this.findAngles(theta, phi).selectIndices({|angle|
			angle.perform(lessThan, spread.half)
		})
	}

	nearestPoints { |theta = 0, phi = 0, spread = 0.5pi, inclusive = true|
		var lessThan;
		inclusive.if({lessThan = '<='}, {lessThan = '<'});
		^this.points.select({|point|
			this.vec_angle(Spherical(1, theta, phi), point).perform(lessThan, spread.half)
		})
	}

	nearestIndicesOrder { |theta = 0, phi = 0, spread = 0.5pi, inclusive = true|
		^this.nearestIndices(theta, phi, spread, inclusive)[this.findAngles(theta, phi)[this.nearestIndices(theta, phi, spread, inclusive)].order]
	}

	nearestPointsOrder { |theta = 0, phi = 0, spread = 0.5pi, inclusive = true|
		^points[this.nearestIndicesOrder(theta, phi, spread, inclusive)]
	}

	findSubset { |numPoints|
		var newTDesign;
		newTDesign = TDesign.new(numPoints);
		^newTDesign.points.collect{|point|
			this.nearestPoint(point.theta, point.phi)
		}
	}

	directions { ^points.collect(_.asSpherical) }

	numPoints { ^points.size }

	size { ^points.size }

	// reset points to position when first created
	reset { this.points_(initPoints) }

	prSaveInitState { initPoints = points }

	points_ { |cartesianArray|
		points = cartesianArray;
		this.changed(\points, points); // TODO: avoid broadcasting points?
	}

	// azElArray: 2D array containing [azimuth, elevation] (theta, phi) pairs
	directions_ { |azElArray|
		this.points_(
			azElArray.collect{ |dirs| Spherical(1, *dirs).asCartesian}
		);
	}

	// showConnections will draw triangular connections between points
	visualize { |parent, bounds, showConnections = true|
		if (showConnections and: { triplets.isNil }) {
			try { this.calcTriplets } { "Could not calculate triplets".warn }
		};

		view = PointView(parent, bounds).points_(points).front;
		this.addDependant(view);
		view.onClose_({ view.onClose.addFunc({ this.removeDependant(view) }) });

		if (showConnections and: { triplets.notNil }) {
			view.connections_(triplets);
		};
	}

}


TDesign : SphericalDesign {
	var <t, nPnts, dim;

	*new { |nPnts, t, dim = 3|
		^super.new.init(nPnts, t, dim);
	}

	init { |aNp, aT, aDim|
		var path, data;

		nPnts = aNp;
		t = aT;
		dim = aDim;

		TDesignLib.lib ?? {TDesignLib.initLib};

		// update instance vars in case not all are specified by *new
		// errors out if no match or multiple matches found
		#nPnts, t, dim = this.prFindDesignMatch;

		path = TDesignLib.path +/+ "des.%.%.%.txt".format(dim, nPnts, t);
		if (File.exists(path).not) {
			"No t-design file found at %".format(path).throw
		};

		data = FileReader.read(path);

		points = data.collect(_.asFloat).flat.clump(3).collect{ |xyz|
			Cartesian(*xyz)
		};

		this.prSaveInitState;
	}

	prFindDesignMatch {
		var matches, m;

		matches = TDesignLib.getDesign(nPnts, t, dim);

		case
		{ matches.size == 0 } {
			"[TDesign:-init] No t-designs found in TDesignLib.lib matching "
			"nPnts %, t %, dim %".format(nPnts, t, dim).throw
		}
		{ matches.size > 1 } {
			var e;
			e = Error(
				"[TDesign:-init] Multiple t-designs found, specify both 'nPnts' "
				"and 't' to return one result. Available designs:"
			);
			e.errorString.postln;
			matches.do(_.postln);
			e.throw;
		}
		{ m = matches[0] };

		// unpack the dictionary to set instance vars on return
		^[m[\numPoints], m[\t], m[\dim]]
	}
}

// A class to download, import, sort and retrieve T-Designs.
// http://neilsloane.com/sphdesigns/
TDesignLib {
	classvar <lib;   // Array of designs, stored as Dictionaries
	classvar <>path;
	// TODO: resolve default path
	classvar <defaultPath = "~/Library/Application Support/ATK/t-designs/";

	*initLib {
		var pn, dim, nPnts, t;

		this.path ?? {this.path = defaultPath.standardizePath};

		if (File.exists(path)) {
			pn = PathName(path);
			if (pn.files.size == 0) {
				Error(
					format(
						"[TDesignLib:*initLib] No t-design files found at %\n"
						"Set TDesignLib.path to the location of your t-design files, \n"
						"or use TDesignLib.downloadAll to download them if you don't yet have them.",
						pn.fullPath
					)
				).throw;
			}
		} {
			Error("[TDesignLib:*initLib] No file exists at path %".format(this.path)).throw;
		};

		lib = List();

		pn.filesDo({ |f|
			#dim, nPnts, t = f.fileNameWithoutExtension.drop(4).split($.).asInt;
			lib.add(
				Dictionary.newFrom([
					\dim, dim, \numPoints, nPnts, \t, t
				]);
			);
		});
	}


	// Download all of the t-designs.
	// NOTE: uses curl, may not be suitable for Windows
	*downloadAll { |savePath, makeDir = false|
		var p = savePath ?? {defaultPath};

		if (File.exists(p).not) {
			if (makeDir) {
				File.mkdir(p)
			} {
				format(
					"[TDesignLib:*downloadAll] Save path doesn't exist. "
					"Set makeDir=true to create it. [%]",
					path
				).throw
			}
		};

		// parse filenames from t-design repository and iteratively download each
		postf("Downloading t-designs to %\nPlease wait ...", p);
		unixCmd(
			format(
				"curl -s http://neilsloane.com/sphdesigns/dim3/ | "
				"grep href | grep \".txt\" | sed 's/.*href=\"//' | sed 's/\".*//' | "
				"while read -r fname; do curl -o %$fname -f http://neilsloane.com/sphdesigns/dim3/$fname; done",
				p.asCompileString
			),
			action: { |...args|
				if (args[0] != 0) {
					"Could not download t-designs from http://neilsloane.com/sphdesigns/dim3/".throw;
				};
				this.path = p;
				this.initLib;
				"Done downloading t-designs.".postln
			},
			postOutput: true
		);
	}

	// post all designs, and if none found locally,
	// retrieve available designs online
	*availableDesigns {
		var res;

		// check locally for loaded designs
		if (lib.notNil) {
			res = lib
		} {
			try {
				this.initLib;
				res = lib;
			} { |error|
				// couldn't load the library
				error.errorString.warn;

				"No local designs are available, but the following "
				"can be downloaded by calling *download.".postln;

				// report what's available online
				res = unixCmdGetStdOut(
					format(
						"curl -s http://neilsloane.com/sphdesigns/dim3/ | "
						"grep href | grep \".txt\" | sed 's/.*href=\"//' | sed 's/\".*//'",
						this.path.asCompileString
					)
				).split($\n).collect({ |me| me.drop(5).reverse.drop(4).reverse });

			};
		};

		res.do(_.postln);
	}

	// return an Array of designs matching the criteria
	*getDesign { |nPnts, t, dim = 3|

		lib ?? {this.initLib};

		^lib.select{ |item|
			var t1, t2, t3;
			t1 = (nPnts.isNil or: { item[\numPoints] == nPnts });
			t2 = t.isNil or: { item[\t] == t };
			t3 = item[\dim] == dim;
			t1 and: t2 and: t3;
		}
	}

}
