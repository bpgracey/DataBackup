# FlipBoard challenge

## Challenge spec

> Your goal is to write a program that backs up all rows of a relational DB table as CSV files and
then uploads them to S3. A user should be able to provide the table name and the location in
S3.

> Please include a short README explaining your approach to the problem and any notes we’ll
need to execute your software.

## Solution

Implementation is Java 8. All libraries used are available on Maven Central. Properties used as parameters, which can be supplied in a file or on the command line; selected [Typesafe Config](https://github.com/lightbend/config) for this. 

Spec breaks down into 3 parts:

1. Access the database
2. Convert table rows to CSV
3. Export CSV lines to S3 bucket.

One consideration I have that's not in the spec is that I don't want to be downloading the entire table to local storage (file or memory), before uploading it to S3 (the usual approach, as S3 likes to know the size of an object before upload starts). That's not a good solution for very large tables.

One early decision: backup the whole database, and rely on S3's versioning feature. Decision made purely to save time.

Another: provide output to console and to file. Essential for testing.

### Connect to database

Used JDBC. This makes selecting a different database engine simply a matter of (a) supplying a jdbc URL in the correct format, and (b) placing the appropriate connector library on the classpath. Additionally, JDBC metadata allows for retrieving column names & structure in a common manner between databases - I used this to exclude generated columns from the data. *Note: storing the database password in the configuration files (as I've done here) - or passing it on a parameter line - is not good practice!*

### Convert rows to CSV

Selected [OpenCSV](http://opencsv.sourceforge.net/), essentially because this library has a method (writeAll) that takes a JDBC RecordSet and a Writer, and processes the RecordSet to the Writer.

By using a PipedOutputStream/PipedInputStream pair, I was able to feed the output from the conversion to an output thread. I opted to run the conversion in a new thread, and the output in the main thread - this decision was based on speed of implementation, a 'better' solution might have been to use separate threads for both - or an actor-model library, such as [Akka](https://akka.io/).

### Export to S3

After much research, I found [S3-Stream-Upload](https://github.com/alexmojaki/s3-stream-upload), a small library that uses S3's multipart-upload feature for very large objects that can't be sized in advance.

## Runtime Notes

1. The S3 code has **NOT** been tested, and the AWS login probably won't work as coded. This should be an easy fix.
2. The application is managed via its configuration. The Config library, by default, gives priority to command-line parameters (**-D** switches), then looks for an application.conf/application.json/application.properties file on the classpath, and finally for a reference.conf (here, supplied in the code) for the defaults. So, database URL, table name, user id and password can all be overriden, permanently in a configuration file on the classpath, or at runtime on the commandline.
3. Some logging has been defined, using [SLF4J](http://www.slf4j.org/) - placing the appropriate connectors & libraries on the classpath should allow logging via most major frameworks.

## Build Notes

1. The build is defined for [Maven](https://maven.apache.org/) and requires access to Maven Central (or a suitable facade site) to download dependencies.
2. if tests are enabled during the build, the app will attempt to connect to the supplied database and print the first thousand records (and a header line) to the console.
3. For speed, this was developed on Windows 10 - but no features not available on Linux were used. *(Usually I spin up an Ubuntu VM for development work, but, for some reason, the last Windows update seems to have knocked out my PC's ability to run VMs!)*
1. Larger databases might require tweaking of the parameters for S3-Stream-Upload.

## Design Notes

Classes in the `config` package support the configuration layout. I chose to use POJOs, as this approach includes implicit configuration validation.
The root package contains a single main class, `App`, that loads the configuration & runs the process.
The `backup` pachage contains a single class file, `backup`, that holds 2 classes - `Backup`, the class that does the connections and manages the output, and `Converter`, a `Runnable` that `Backup` sets running on its own thread, to process the table.

## Work left undone

1. Testing the S3 code!
1. Checking that the object has been uploaded to the S3 bucket.
1. Unit & integration tests!
1. Instead of building raw SQL, use a library to write the SQL statement in the appropriate dialect?

# Considerations
- **What type of DB?**

We're going from a single table in a relational (SQL) database to a single text-file representation of the contents

- **How often does your solution need to run?**

Not enough info to answer precisely. If this is for disaster management, often enough to be able to recover the database and not be too out of date. If this is for auditing, then external factors will apply. If this is for data analysis - then this is the wrong solution!

- **How would you handle full vs delta dumps? What are the considerations for each?**

This, as implemented, is a full dump. A delta would imply the database keeping track of changes to the table, and I'd be acquiring that changeset & storing it in some suitable form - and providing a way to reconstruct the data, since the purpose of a backup is to have a snapshot of the data at a particular moment. Taking a regular full dump, so the stack of deltas doesn't become too large (for some values of large), is generally good practice (but may be problematic with very large, highly-active tables)

- **How would you distribute this across multiple nodes (maybe we have a really, really large table?)**

Insufficient data to decide. Possibly use different buckets or S3 objects for each node? Ideally using the original primary keys, though that's not required - the format chosen doesn't support fast access.

- **How could we recover from errors along the way without repeating steps?**

if the error is in retrieving the recordset - perhaps a glitch in the connection - restarting the query at the same point should suffice (ie. dropping the first x records). But if it's an error on the S3 side, then we're stumped - S3 doesn't store incomplete objects, so we'd have to start over. If the conversion process throws an error, then restarting the query from the same point may work - but most likely the converter has hit something it doesn't understand, and the run will have to be aborted. Storing copies of the intermediate upload objects, in case we have to start the S3 upload again, seems like overkill - if we do that, why not build a single object in local memory in the first place?

- **How would you verify a job is successful?**

Verify the object was written to the bucket (AWS won't confirm this; S3 docs say we should assume the upload worked if we don't get an error. I'm not a big fan of this idea.). if possible, retrieve a sampling of records from the object and compare them with the same rows in the source table.