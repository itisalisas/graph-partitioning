# graph-partitioning

## Table of Contents
- [Description](#description)
- [Usage](#usage)
- [License](#license)


## Description

This is an application designed for balanced partitioning of planar graphs using various algorithms.
The project considers algorithms "Inertial Flow" and "Bubble algorithm" and/or their modifications.

## Usage


### Running the Application

This project uses Gradle as a build tool. 

The program takes the following arguments in the command line:

- `algorithm-name`: The type of the algorithm to use for partitioning. Currently, the supported algorithm is "Inertial Flow", which can be specified as `IF`.

- `path-to-file`: The path to the input file that describes the graph to be partitioned.

- `max-sum-vertices-weight`: The maximum total weight of the vertices in the partition parts.

- `output-directory`: The name of the directory where the partition files will be written. The partition files will be named in the format `partition_*.txt`.

To run the application, execute the following command:

```bash
git clone https:/github.com/itisalisas/graph-partitioning.git

cd graph-partitioning

./gradlew build

./gradlew run --args="<algorithm-name> <path-to-file> <max-sum-vertices-weight> <output-directory> [param] ..." 
```


## License

[MIT License](https://choosealicense.com/licenses/mit/)
