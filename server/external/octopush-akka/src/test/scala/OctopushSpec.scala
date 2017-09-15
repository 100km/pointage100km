import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import net.rfc1149.octopush.Octopush._
import net.rfc1149.octopush.{ErrorCodes, Octopush}
import org.specs2.mutable._
import org.specs2.specification.Scope

import scala.concurrent.Await
import scala.concurrent.duration._

class OctopushSpec extends Specification {

  trait OctopushScope extends Scope with After {

    val octopush: Octopush

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher
    implicit val materializer = ActorMaterializer()

    override def after = {
      system.terminate()
    }

  }

  trait DummyOctopushScope extends OctopushScope {

    val octopush = new Octopush("dummy@example.com", "dummyApiKey")

  }

  trait ValidOctopushScope extends OctopushScope {

    val octopush = (System.getenv("OCTOPUSH_USER_LOGIN"), System.getenv("OCTOPUSH_API_KEY")) match {
      case (null, _) ⇒
        skipped("no octopush credentials in environment"); null
      case (userLogin, apiKey) ⇒ new Octopush(userLogin, apiKey)
    }

  }

  "The unmarshallers" should {

    "decode the balance web site example" in new DummyOctopushScope {
      val response =
        <octopush>
          <balance type="FR">1251.80</balance>
          <balance type="XXX">1829.40</balance>
        </octopush>
      val balance = Await.result(balanceUnmarshaller(response), 1.second)
      balance.premiumFrance must be equalTo 1251.80
      balance.lowCostFrance must be equalTo 1829.40
    }

    "decode the credit web site example" in new DummyOctopushScope {
      val response =
        <octopush>
          <credit>5623.34</credit>
        </octopush>
      Await.result(creditUnmarshaller(response), 1.second) must be equalTo 5623.34
    }

    "decode the SMS sending web site example" in new DummyOctopushScope {
      val response =
        <octopush>
          <error_code>000</error_code>
          <cost>0.105</cost>
          <balance>6.93</balance>
          <ticket>api110000000021</ticket>
          <sending_date>1326311820</sending_date>
          <number_of_sendings>1</number_of_sendings>
          <currency_code>€</currency_code>
          <successs>
            <success>
              <recipient>+33601010101</recipient>
              <country_code>FR</country_code>
              <cost>0.550</cost>
            </success>
          </successs>
          <failures/>
        </octopush>
      val result = Await.result(smsResultUnmarshaller(response), 1.second)
      result.cost must be equalTo 0.105
      result.balance must be equalTo 6.93
      result.ticket must be equalTo "api110000000021"
      result.sendingDate must be equalTo 1326311820L
      result.numberOfSendings must be equalTo 1
      result.currencyCode must be equalTo "€"
      result.successes must have size 1
      val success = result.successes.head
      success.recipient must be equalTo "+33601010101"
      success.countryCode must be equalTo "FR"
      success.cost must be equalTo 0.55
    }

  }

  "retrieving the balance" should {

    "signal an error if the credentials are wrong" in new DummyOctopushScope {
      Await.result(octopush.balance(), 5.seconds) should throwA[APIError]("101")
    }

    "return a plausible value" in new ValidOctopushScope {
      Await.result(octopush.balance(), 5.seconds).lowCostFrance should be greaterThanOrEqualTo 0.0
      Await.result(octopush.balance(), 5.seconds).premiumFrance should be greaterThanOrEqualTo 0.0
    }

  }

  "retrieving the account credit" should {

    "signal an error if the credentials are wrong" in new DummyOctopushScope {
      Await.result(octopush.credit(), 5.seconds) should throwA[APIError]("101")
    }

    "return a plausible value" in new ValidOctopushScope {
      Await.result(octopush.credit(), 5.seconds) should be greaterThanOrEqualTo 0.0
    }
  }

  "sending a SMS" should {

    "signal an error if the credentials are wrong" in new DummyOctopushScope {
      val sms = SMS(smsRecipients = List("+33601010101"), smsText = "Hi, this is a SMS", smsType = LowCostFrance)
      val result = octopush.sms(sms)
      Await.result(result, 5.seconds) should throwA[APIError]("101")
    }

    "accept a SMS in test mode" in new ValidOctopushScope {
      val sms = SMS(smsRecipients = List("+33601010101"), smsText = "Hi, this is a SMS", smsType = LowCostFrance, simulation = true)
      val result = Await.result(octopush.sms(sms), 5.seconds)
      result.numberOfSendings should be equalTo 1
      result.successes must have size 1
      result.currencyCode must be equalTo "€"
      val success = result.successes.head
      success.recipient must be equalTo "+33601010101"
      success.cost must be equalTo result.cost
    }

    "accept a SMS in test mode with SHA1 checksum" in new ValidOctopushScope {
      val sms = SMS(smsRecipients = List("+33601010101"), smsText = "Hi, this is a SMS", smsType = LowCostFrance, simulation = true,
                    requestKeys   = "TRY")
      val result = Await.result(octopush.sms(sms), 5.seconds)
      result.numberOfSendings should be equalTo 1
      result.successes must have size 1
      result.currencyCode must be equalTo "€"
      val success = result.successes.head
      success.recipient must be equalTo "+33601010101"
      success.cost must be equalTo result.cost
    }

    "be impossible when attempting to checksum non-existing fields" in new DummyOctopushScope {
      val sms = SMS(smsRecipients = List("+33601010101"), smsText = "Foo", smsType = LowCostFrance, requestKeys = "b")
      Await.result(octopush.sms(sms), 5.seconds) must throwA[IllegalArgumentException]("no value defined for key")
    }

    "be impossible when attempting to checksum over unknown keys" in new DummyOctopushScope {
      val sms = SMS(smsRecipients = List("+33601010101"), smsText = "Foo", smsType = LowCostFrance, requestKeys = "z")
      Await.result(octopush.sms(sms), 5.seconds) must throwA[IllegalArgumentException]("unknown key")
    }

  }

  "error codes" should {

    "be lookable" in {
      ErrorCodes.errorMessage.get(101) must be equalTo Some("Mauvais identifiants")
    }

    "be retrievable from APIError exceptions" in {
      (throw APIError(101)).asInstanceOf[String] must throwA[APIError]("Mauvais identifiants")
    }
  }

}
