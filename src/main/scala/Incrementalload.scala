import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._

object Incrementalload {
  def main(args: Array[String]): Unit = {
    // Initialize SparkSession
    val spark = SparkSession.builder()
      .appName("Incrementalload")
      .config("spark.master", "local[*]")  // Adjust as per your deployment environment
      .enableHiveSupport()
      .getOrCreate()

    try {
      // Read data from PostgreSQL table
      val df = spark.read.format("jdbc")
        .option("url", "jdbc:postgresql://ec2-3-9-191-104.eu-west-2.compute.amazonaws.com:5432/testdb")
        .option("dbtable", "nvidia_stock_price")
        .option("driver", "org.postgresql.Driver")
        .option("user", "consultants")
        .option("password", "WelcomeItc@2022")
        .load()

      // Print schema and sample data from PostgreSQL
      df.printSchema()
      df.show(5)

      // Read existing data from Hive table
      val existing_hive_data = spark.read.table("ammad.nvidia_stock_price")
      existing_hive_data.show(5)

      // Handle null values in 'job' column before transforming
//      val dfFiltered = df.withColumn("job_upper", when(col("job").isNull, lit(null)).otherwise(upper(col("job"))))
//      dfFiltered.show(5)

      // Determine incremental data using left_anti join
      val incremental_data_df = df.join(existing_hive_data, Seq("Date"), "left_anti")
      println("------------------Incremental data-----------------------")
      incremental_data_df.show(5)

      // Count new records added to PostgreSQL table
      val new_records = incremental_data_df.count()
      println("------------------COUNTING INCREMENT RECORDS ------------")
      println(s"New records added count: $new_records")

      // Append incremental_data_df to the existing Hive table if there are new records
      if (new_records > 0) {
        incremental_data_df.write.mode("append").saveAsTable("ammad.nvidia_stock_price")
        println("New records appended to Hive table.")
      } else {
        println("No new records appended to Hive table.")
      }

      // Read updated data from Hive table and display ordered by id descending
      val updated_hive_data = spark.read.table("ammad.nvidia_stock_price")
      val df_ordered = updated_hive_data.orderBy(col("Date").desc_nulls_last)
      println("Updated Hive table:")
      df_ordered.show(5)
    } finally {
      // Stop SparkSession at the end
      spark.stop()
    }
  }
}
