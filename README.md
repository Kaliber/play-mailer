*Job opening: Scala programmer at Rhinofly*
-------------------------------------------
Each new project we start is being developed in Scala. Therefore, we are in need of a [Scala programmer](http://rhinofly.nl/vacature-scala.html) who loves to write beautiful code. No more legacy projects or maintenance of old systems of which the original programmer is already six feet under. What we need is new, fresh code for awesome projects.

Are you the Scala programmer we are looking for? Take a look at the [job description](http://rhinofly.nl/vacature-scala.html) (in Dutch) and give the Scala puzzle a try! Send us your solution and you will be invited for a job interview.
* * *

Scala mailer module for Play 2.1.0
=====================================================

Scala wrapper around java mail which allows you to send emails. The default configuration options exposed in Configuration work using  Amazon SES SMTP

Installation
------------

``` scala
  val appDependencies = Seq(
    "play.modules.mailer" %% "play-mailer" % "1.1.0"
  )
  
  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += "Rhinofly Internal Release Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
  )
```

Configuration
-------------

`application.conf` should contain the following information:

``` scala
mail.from.name=From name
mail.from.address="validated email address or email adress on validated domain"
mail.smtp.failTo="failto+customer@company.org"

mail.smtp.host=email-smtp.us-east-1.amazonaws.com
mail.smtp.port=465
mail.smtp.username="Smtp username as generated by Amazon"
mail.smtp.password="Smtp password"
```
`application.conf` can additionally contain the following information:
``` scala
#default is smtps
mail.transport.protocol=smtp
#default is true
mail.smtp.ssl.enable=true
```

Usage
-----

* Synchronous API

``` scala
  import play.modules.mailer._

  Mailer.sendEmail(Email(
    subject = "Test mail",
    from = EmailAddress("Erik Westra sender", "ewestra@rhinofly.nl"),
    replyTo = None,
    recipients = List(Recipient(Message.RecipientType.TO, EmailAddress("Erik Westra recipient", "ewestra@rhinofly.nl"))),
    text = "text",
    htmlText = "htmlText",
    attachments = Seq.empty))
```

* Reactive API

``` scala
    import play.modules.mailer._

    AsyncMailer.sendEmail(Email(
      subject = "Test mail",
      from = EmailAddress("Erik Westra sender", "ewestra@rhinofly.nl"),
      replyTo = None,
      recipients = List(Recipient(Message.RecipientType.TO, EmailAddress("Erik Westra recipient", "ewestra@rhinofly.nl"))),
      text = "text",
      htmlText = "htmlText",
      attachments = Seq.empty))
```
