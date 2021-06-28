# BICEPS

[BEAST 2](http://beast2.org) package for Bayesian Integrated Coalescent Epoch PlotS + Yule Skyline models

## Installation

* Install BEAST 2 (available from [http://beast2.org](http://beast2.org)).
* Add https://raw.githubusercontent.com/CompEvol/CBAN/master/packages-extra.xml to your package repositories
	* Open BEAUti, select `File => Manage packages` menu. The package manager dialog pops up.
	* Click the `Package Repositories` button. The repositories dialog pops up.
	* Click `Add URL`
	* Enter `https://raw.githubusercontent.com/CompEvol/CBAN/master/packages-extra.xml` in the entry, and click `OK`
	* Click the `Done` button, and starbeast3 should appear in the package list.
* Install BICEPS package through the [package manager](http://www.beast2.org/managing-packages/) (this may automatically install BEASTLabs as well if it is not already installed)

## Using BICEPS

* Start BEAUti, and select the Standard template  (menu `File/Templates/Standard`)
* Use BEAUti in to import an alignment
* In the priors tab, select `BICEPS` or `Yule Skyline` from the drop down menu associated with the tree prior
* Run BEAST on the XML file saved from BEAUti


## Questions about BICEPS

BEAST user list: [https://groups.google.com/forum/#!forum/beast-users](https://groups.google.com/forum/#!forum/beast-users)

Remco Bouckaert: [rbouckaert@auckland.ac.nz](rbouckaert@auckland.ac.nz)
