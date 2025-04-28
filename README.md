CCS Contract Award Service Tenders API
===========

Overview
--------
This is the code for the user interface of Crown Commercial Service's (_CCS_)
Tenders API, used by the Contract Award Service (_CAS_).

The specification for the API can be found in the [Open API Specification][].

Technology Overview
---------
The project is implemented as a Spring Boot 3 web application, implemented using Maven.

The core technologies for the project are:

* Java 23
* [Spring Boot][]
* [Spring Security][]
* [Ehcache][] for caching
* [JUnit][] for unit testing

Building and Running Locally
----------------------------
To run the application locally, you simply need to run the core ccs-scale-cat-service module.

In order to generate required classes for the application, you need to use Maven to _**Generate Sources and Update Folders**_.

You will need to be supplied with a local secrets file (`resources/application-local.yml`) to enable the project to run, which can be supplied by any member of the development team.

You will also need to be supplied with a local SQL file (`resources/data.sql`) to enable the project to spool up the necessary local database for you at runtime, which can again be supplied by any member of the development team.

Once the application has started it can be accessed via Postman using the URL http://localhost:8080/.

Branches
--------
When picking up tickets, branches should be created using the **_feature/*_** format.

When completed, these branches should be pull requested against _**develop**_ for review and approval.  _**develop**_ is then built out onto the **Development** environment.

The **UAT** and **Pre-Production** environments are controlled via means of release and hotfix branches.

Release branches should be created from _**develop**_ using the **_release/*_** format, whilst hotfixes should be created from _**main**_ using the **_hotfix/*_** format.  These branches can then be built out to **UAT** and **Pre-Production** as appropriate.

When releases/hotfixes are ready for deployment to **Production**, the **_release/*_** or **_hotfix/*_** branch in question should be pull requested against the _**main**_ branch for review and approval.  This branch should then be built out to **Production**.

Once a release/hotfix has been completed you should be sure to merge _**main**_ back down into _**develop**_.

[Spring Boot]: https://spring.io/projects/spring-boot
[Spring Security]: https://spring.io/projects/spring-security
[JUnit]: https://junit.org/junit5/
[Ehcache]: https://www.ehcache.org/
[Open API Specification]: https://github.com/Crown-Commercial-Service/ccs-scale-api-definitions/blob/master/cat/CaT-service.yaml