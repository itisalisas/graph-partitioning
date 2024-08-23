# graph-partitioning

## Table of Contents
- [Description](#description)
- [File format](#fileformat)
- [Usage](#usage)
- [Scripts](#scripts)
- [License](#license)


## Description

This is an application designed for balanced partitioning of planar graphs using various algorithms.
The project considers algorithms "Inertial Flow" and "Bubble algorithm" and/or their modifications.

## File format

Input or output files with graphs in the format:
```bash
file format: 	n (Vertices number)
		name x y (of Vertex) n1 (Number of outedges) name1 x1 y1 (of out vertex) length1 (edge length) ...
		long double x2		long 			long double x2		 double
```

## Usage


### Running the Application

This project uses Gradle as a build tool. 

The program takes the following arguments in the command line:

- `algorithm-name`: The type of the algorithm to use for partitioning. Currently, the supported algorithm is "Inertial Flow", which can be specified as `IF`.

- `path-to-file`: The path to the input file that describes the graph to be partitioned. (From graph-partitioning/src/main/resources/)

- `max-sum-vertices-weight`: The maximum total weight of the vertices in the partition parts.

- `output-directory`: The name of the directory where the partition files will be written. (From graph-partitioning/src/main/output/) The partition files will be named in the format `partition_*.txt`.

To run the application, execute the following command:

```bash
git clone https:/github.com/itisalisas/graph-partitioning.git

cd graph-partitioning

./gradlew build

./gradlew run --args="<algorithm-name> <path-to-file> <max-sum-vertices-weight> <output-directory> [param] ..." 
```

Example:

```bash
./ gradlew run --args="IF dataExample\\graph_59.93893094417527_30.32268115454809_1500.txt 1000 59.93893094417527_30.32268115454809_1500"

```

## Scripts

### AdjacencyListFromOSM

This is an application for getting the adjacency list of a graph from OpenStreetMaps. It is necessary to select the coordinates of the center and the distance around to determine the area from where information about the graph is extracted. The graph format is an adjacency list, see the "file format" for more details.

## License

[MIT License](https://choosealicense.com/licenses/mit/)
