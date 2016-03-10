package tm.hillary

import tm.test.BaseSpec
import java.util.regex.Pattern
import scala.annotation.tailrec
import tm.text.Preprocessor
import tm.text.StopWords


class ConvertSpec extends BaseSpec {

    import Converter._
    import Preprocessor._
    implicit val stopwords = StopWords.read("stopwords.csv")
    
    trait DictionaryFrom2ndEmail {
        val dictionary = Set("thursday", "aiding", "docx", "hillary",
            "libya", "march", "memo", "qaddafi", "syria",
            "syria-aiding", "libya-docx")
    }

    trait Words {
        val words = List("aiding", "docx", "hillary")
        val counts = List(3, 2, 1)
        def wordCounts = words.zip(counts).toMap
        def singleCounts = words.map(w => Map(w -> 1))
    }

    describe("The counts of the three words") {
        they("should add up correctly") {
            new Words {
                val aidingSingle = singleCounts(0)
                val aidingCount1 = add(aidingSingle, aidingSingle)
                aidingCount1.size should equal(1)
                aidingCount1("aiding") should equal(2)
            }
        }
    }

    describe("Hillary Emails") {
        describe("The second email") {
            trait SecondEmail extends Emails {
                Given("The second email")
                val email = bodies.drop(1).head
            }

            it("should allow the number of words to be counted correctly") {
                new SecondEmail {
                    When("the words are counted")
                    val counts = tokenizeAndCount(email)

                    Then("there should be 9 distinct non-stop-words")
                    counts.size should equal(9)

                    And("The word aiding should have 3 occurences")
                    counts("aiding") should equal(3)
                }
            }

            it("should allow n-grams (n=1,2,3) to be found properly") {
                new SecondEmail {
                    When("the words are counted")
                    val counts = tokenizeAndCount(email, 3)

                    Then("there should be 32 distinct n-grams")
                    counts.size should equal(32)

                    And("The unigram aiding should have 3 occurences")
                    counts("aiding") should equal(3)

                    And("The bigram syria-aiding should have 3 occurences")
                    counts("syria-aiding") should equal(3)

                    And("The trigram memo-syria-aiding should have 3 occurences")
                    counts("memo-syria-aiding") should equal(2)
                }
            }

            it("should produce tokens properly without constituent tokens") {
                new SecondEmail with DictionaryFrom2ndEmail {
                    When("the tokens are produced and constituent tokens are removed")
                    val words = tokenizeBySpace(email)

                    val tokens1 = tokenizeWithoutConstituentTokens(
                        words, dictionary.contains, 1)
                    val tokens2 = tokenizeWithoutConstituentTokens(
                        words, dictionary.contains, 2)

                    //                    val tokens1 = tokenizeWithoutConstituentTokens(
                    //                        words, dictionary.contains, 1)
                    //                    val tokens2 = tokenizeWithoutConstituentTokens(
                    //                        words, dictionary.contains, 2)

                    Then("The token list containing 1-grams should be found correctly")
                    tokens1 should contain theSameElementsAs Vector(
                        "thursday", "march", "syria", "aiding", "qaddafi",
                        "memo", "syria", "aiding", "libya", "docx", "memo",
                        "syria", "aiding", "libya", "docx", "march", "hillary")

                    Then("The tokens list containing 1-grams and 2-grams should be correct")
                    tokens2 should contain theSameElementsAs Vector(
                        "thursday", "march", "syria-aiding", "qaddafi",
                        "memo", "syria-aiding", "libya-docx", "memo",
                        "syria-aiding", "libya-docx", "march", "hillary")
                }
            }
        }

        describe("The first 10 emails") {
            they("should allow number of words to be counted correctly") {
                new Emails {
                    Given("The first 10 emails")
                    val countsByEmails = countWordsInEmails(10)

                    When("the term frequencies are computed")
                    val counts = sumWordCounts(countsByEmails)

                    checkNumberOfWords(Then)(counts, 70)
                    checkWordOccurence(And)(counts, "aiding", 7)
                    checkWordOccurence(And)(counts, "libya", 8)
                }
            }

            they("should allow document frequencies to be computed correctly") {
                new Emails {
                    Given("The first 10 emails")
                    val countsByEmails = countWordsInEmails(10)

                    When("the document frequencies are computed")
                    val documentFrequencies =
                        computeDocumentFrequencies(countsByEmails)

                    checkNumberOfWords(Then)(documentFrequencies, 70)

                    checkDocumentFrequency(And)(documentFrequencies, "aiding", 3)
                    checkDocumentFrequency(And)(documentFrequencies, "libya", 5)
                }
            }

            they("should allow tf-idf to be computed correctly") {
                new Emails {
                    Given("The first 10 emails")
                    val countsByEmails = countWordsInEmails(10)

                    When("tf-idf are computed")
                    val tfidf = computeTfIdf(countsByEmails)

                    checkNumberOfWords(Then)(tfidf, 70)

                    checkTfIdf(And)(tfidf, "aiding", 8.4278)
                    checkTfIdf(And)(tfidf, "libya", 5.5452)
                }
            }

            they("should allow correct selection of words over 5 occurrences") {
                new Emails {
                    Given("The first 10 emails")
                    val countsByEmails = countWordsInEmails(10)

                    When("The dictionary is built")
                    val dictionary = buildDictionary(countsByEmails)
                        .filter(_.tf > 5)

                    val tfidf = dictionary.getMap(_.tfidf)
                    checkNumberOfWords(Then)(tfidf, 3)

                    checkTfIdf(And)(tfidf, "aiding", 8.4278)
                    checkTfIdf(And)(tfidf, "libya", 5.5452)

                    And("The word anti is filtered out")
                    tfidf.contains("anti") should be(false)
                }
            }

            they("should allow the proper building of bow representation") {
                new Emails {
                    Given("The first 10 emails")
                    val countsByEmails = countWordsInEmails(10)

                    When("The data is converted to bow")
                    val dictionary = buildDictionary(countsByEmails).filter(_.tf > 5)
                    val bow = convertToBow(countsByEmails, dictionary.map).toVector

                    Then("The words should be aiding, syria, and libya")
                    dictionary.words should contain theSameElementsAs Vector("aiding", "syria", "libya")

                    And("The first and third email should contain exactly three zero counts")
                    bow(0) should contain theSameElementsAs Vector(0, 0, 0)
                    bow(2) should contain theSameElementsAs Vector(0, 0, 0)

                    And("The second email should contain correct counts")
                    bow(1) should contain theSameElementsAs Vector(3, 3, 2)

                    And("The bow should have correct number of instances")
                    bow.length should equal(10)
                }
            }
        }
    }

    describe("Hillary Emails") {
        they("should contain only proper characters after preprocessing") {
            new Emails {
                Given("all emails")
                When("the emails are preprocessed and are converted to words")
                val words = super.bodies.flatMap(_.split("\\s+")).toSet

                Then("there should not be any words with non-alphabet characters")
                words.filter(_.matches(".*\\P{Alnum}+.*")) shouldBe empty
            }
        }
    }

    def findAllPattern(regex: String): (String) => Seq[String] = {
        (input: String) =>
            {
                val matcher = Pattern.compile(regex).matcher(input)

                @tailrec
                def rec(matches: List[String]): List[String] =
                    if (matcher.find())
                        rec(matcher.group() :: matches)
                    else
                        matches

                rec(List.empty)
            }
    }

    def checkNumberOfWords(informer: (String) => Unit)(map: Map[String, _], size: Int) = {
        informer(s"there should be ${size} distinct non-stop-words")
        map.size should equal(size)
    }

    def checkDocumentFrequency(informer: (String) => Unit)(
        df: Map[String, Int], word: String, frequency: Int) = {
        informer(s"The word ${word} should have appeared in ${frequency} documents")
        df(word) should equal(frequency)
    }

    def checkWordOccurence(informer: (String) => Unit)(
        counts: Map[String, Int], word: String, count: Int) = {
        informer(s"The word ${word} should have ${count} occurences")
        counts(word) should equal(count)
    }

    def checkTfIdf(informer: (String) => Unit)(
        tfidf: Map[String, Double], word: String, expected: Double) = {
        informer(s"The tf-idf of word ${word} should be correct")
        tfidf(word) should equal(expected +- 5e-5)
    }
}