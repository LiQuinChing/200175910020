-- MySQL dump 10.13  Distrib 26.7.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: training_management
-- ------------------------------------------------------
-- Server version	26.7.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `department`
--

DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `department_id` bigint NOT NULL AUTO_INCREMENT,
  `department_name` varchar(200) NOT NULL,
  `code` varchar(30) NOT NULL,
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `uq_department_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `department`
--

LOCK TABLES `department` WRITE;
/*!40000 ALTER TABLE `department` DISABLE KEYS */;
INSERT INTO `department` VALUES (10,'Finance','DEPT-10'),(20,'Administration','DEPT-20');
/*!40000 ALTER TABLE `department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `officer`
--

DROP TABLE IF EXISTS `officer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `officer` (
  `officer_id` bigint NOT NULL,
  `officer_name` varchar(150) NOT NULL,
  `department_id` bigint DEFAULT NULL,
  `designation` varchar(200) DEFAULT NULL,
  `email` varchar(254) DEFAULT NULL,
  PRIMARY KEY (`officer_id`),
  KEY `fk_officer_department` (`department_id`),
  CONSTRAINT `fk_officer_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `officer`
--

LOCK TABLES `officer` WRITE;
/*!40000 ALTER TABLE `officer` DISABLE KEYS */;
INSERT INTO `officer` VALUES (1001,'Example Officer',NULL,NULL,NULL),(1002,'Example Officer',NULL,NULL,NULL);
/*!40000 ALTER TABLE `officer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `training_nomination`
--

DROP TABLE IF EXISTS `training_nomination`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_nomination` (
  `nomination_id` bigint NOT NULL AUTO_INCREMENT,
  `training_id` bigint NOT NULL,
  `officer_id` bigint NOT NULL,
  `nominated_by_department_id` bigint NOT NULL,
  `nomination_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(20) NOT NULL DEFAULT 'NOMINATED',
  PRIMARY KEY (`nomination_id`),
  UNIQUE KEY `uq_training_officer` (`training_id`,`officer_id`),
  KEY `fk_nomination_officer` (`officer_id`),
  KEY `fk_nomination_department` (`nominated_by_department_id`),
  CONSTRAINT `fk_nomination_department` FOREIGN KEY (`nominated_by_department_id`) REFERENCES `department` (`department_id`),
  CONSTRAINT `fk_nomination_officer` FOREIGN KEY (`officer_id`) REFERENCES `officer` (`officer_id`),
  CONSTRAINT `fk_nomination_training` FOREIGN KEY (`training_id`) REFERENCES `training_programme` (`training_id`),
  CONSTRAINT `chk_nomination_status` CHECK ((`status` in (_utf8mb4'NOMINATED',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `training_nomination`
--

LOCK TABLES `training_nomination` WRITE;
/*!40000 ALTER TABLE `training_nomination` DISABLE KEYS */;
INSERT INTO `training_nomination` VALUES (1,101,1001,10,'2026-09-09 04:48:29','NOMINATED'),(2,102,1002,20,'2026-09-09 04:52:13','NOMINATED'),(3,102,1001,10,'2026-09-09 04:59:55','NOMINATED');
/*!40000 ALTER TABLE `training_nomination` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `training_programme`
--

DROP TABLE IF EXISTS `training_programme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_programme` (
  `training_id` bigint NOT NULL AUTO_INCREMENT,
  `training_name` varchar(200) NOT NULL,
  `training_date` date DEFAULT NULL,
  `venue` varchar(200) DEFAULT NULL,
  `max_participants` int DEFAULT NULL,
  PRIMARY KEY (`training_id`),
  CONSTRAINT `chk_training_capacity` CHECK ((`max_participants` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `training_programme`
--

LOCK TABLES `training_programme` WRITE;
/*!40000 ALTER TABLE `training_programme` DISABLE KEYS */;
INSERT INTO `training_programme` VALUES (101,'Public Administration',NULL,NULL,NULL),(102,'Procurement',NULL,NULL,NULL);
/*!40000 ALTER TABLE `training_programme` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-09 11:11:31
