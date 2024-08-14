# graph-partitioning

## Table of Contents
- [Description](#description)
- [File format](#fileformat)
- [Usage](#usage)
- [License](#license)


## Description

This is an application designed for balanced partitioning of planar graphs using various algorithms.
The project considers algorithms "Inertial Flow" and "Bubble algorithm" and/or their modifications.

## File format

Input files with graphs in the format:
```bash
file format: 	n (Vertices number)
		name x y (of Vertex) n1 (Number of outedges) name1 x1 y1 (of out vertex) length1 (edge length) ...
		long double x2		long 			long double x2		 double
```
Output files with description of partitioning parts in the format:
```bash
file format: 	n (Vertices number)
		name x y (of Vertex) w (weight) ...
		long double x2		long
```

In addition to the partition files, each partition directory also contains an `info.txt` file with summary statistics about the partition. The info.txt file includes the following metrics:

- MIN: The minimum weight of the partition.
- MAX: The maximum weight of the partition.
- AVERAGE: The average weight of the partition.
- VARIANCE: The variance of the weights within the partition.
- CV: The coefficient of variation of the weights.

## Usage


### Running the Application

This project uses Gradle as a build tool. 

The program takes the following arguments in the command line:

- `algorithm-name`: The type of the algorithm to use for partitioning. Currently, the supported algorithm is "Inertial Flow", which can be specified as `IF`.

- `path-to-file`: The path to the input file that describes the graph to be partitioned. (From graph-partitioning/src/main/resources/)

- `max-sum-vertices-weight`: The maximum total weight of the vertices in the partition parts.

- `output-directory`: The name of the directory where the partition files will be written. (From graph-partitioning/src/main/output/) The partition files will be named in the format `partition_*.txt`.

- `param` (optional): The fraction of the weight that must be present in each partition part. If not provided, the default value is 0.25.

To run the application, execute the following command:

```bash
git clone https:/github.com/itisalisas/graph-partitioning.git

cd graph-partitioning

./gradlew build

./gradlew run --args="<algorithm-name> <path-to-file> <max-sum-vertices-weight> <output-directory> [param]" 
```

Example:

```bash
C:\Users\Lenovo\eclipse-workspace\graph-partitioning>gradlew build

C:\Users\Lenovo\eclipse-workspace\graph-partitioning> gradlew run --args="IF dataExample\\graph_59.93893094417527_30.32268115454809_1500.txt 1000 59.93893094417527_30.32268115454809_1500"

```

### Visualization Script
To visualize the partitions, you can use the partition_visualizer.py script. This script generates a map visualization of the partitions.

- `directory_name`: The name of the directory containing the partition files (from graph-partitioning/src/main/output/).

- `output_file.html` : The name of the HTML file where the visualization will be saved.

- The script will save the HTML file in the same directory as the partition files.

Example:

```bash
python3 src/scripts/partition_visualizer.py 59.93893094417527_30.32268115454809_1500 map.html
```

## License

[MIT License](https://choosealicense.com/licenses/mit/)
