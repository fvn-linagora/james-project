/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.methods;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayInputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.ResponseSpecification;

public abstract class SetMessagesMethodTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();
    private JmapServer jmapServer = jmapServer(temporaryFolder, embeddedElasticSearch, cassandra);

    protected abstract JmapServer jmapServer(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra);

    @Rule
    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch)
        .around(jmapServer);

    private AccessToken accessToken;
    private String username;
    private ParseContext jsonPath;

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        String domain = "domain.tld";
        username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", "username", "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);

        jsonPath = JsonPath.using(Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build());
    }

    @Test
    public void setMessagesShouldReturnErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    @Test
    public void setMessagesShouldReturnErrorNotSupportedWhenRequestContainsNonNullIfInState() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"ifInState\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenUnknownMailbox() throws Exception {

        String unknownMailboxMessageId = username + "|unknown|12345";
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + unknownMailboxMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messagesSet\","))
            .body("$", hasSize(1))
            .body("[0][1].destroyed", hasSize(0))
            .body("[0][1].notDestroyed", hasKey(unknownMailboxMessageId))
            .body("[0][1].notDestroyed[\"" + unknownMailboxMessageId + "\"].type", equalTo("anErrorOccurred"))
            .body("[0][1].notDestroyed[\"" + unknownMailboxMessageId + "\"].description", equalTo("An error occurred while deleting message " + unknownMailboxMessageId))
            .body("[0][1].notDestroyed[\"" + unknownMailboxMessageId + "\"].properties", isEmptyOrNullString());
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenNoMatchingMessage() throws Exception {

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        String messageId = username + "|mailbox|12345";
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messagesSet\","))
            .body("$", hasSize(1))
            .body("[0][1].destroyed", hasSize(0))
            .body("[0][1].notDestroyed", hasKey(messageId))
            .body("[0][1].notDestroyed[\"" + messageId + "\"].type", equalTo("notFound"))
            .body("[0][1].notDestroyed[\"" + messageId + "\"].description", equalTo("The message " + messageId + " can't be found"))
            .body("[0][1].notDestroyed[\"" + messageId + "\"].properties", isEmptyOrNullString());
    }

    @Test
    public void setMessagesShouldReturnDestroyedWhenMatchingMessage() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        // When
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messagesSet\","))
            .extract()
            .asString();

        // Then
        assertThat(jsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<Integer>read("$.[0].[1].destroyed.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<Integer>read("$.[0].[1].notDestroyed.length()")).isEqualTo(0);
    }

    @Test
    public void setMessagesShouldDeleteMessageWhenMatchingMessage() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Then
        String getMessagesResponse = given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .content(startsWith("[[\"messages\","))
                .extract()
                .asString();

        assertThat(jsonPath.parse(getMessagesResponse).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(getMessagesResponse).<Integer>read("$.[0].[1].list.length()")).isEqualTo(0);
    }

    @Test
    public void setMessagesShouldReturnDestroyedNotDestroyWhenMixed() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        // When
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\", \"" + username + "|mailbox|4\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messagesSet\","))
            .extract()
            .asString();

        // Then
        assertThat(jsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<Integer>read("$.[0].[1].destroyed.length()")).isEqualTo(2);
        assertThat(jsonPath.parse(response).<Integer>read("$.[0].[1].notDestroyed.length()")).isEqualTo(1);
    }

    @Test
    public void setMessagesShouldDeleteOrNotMessagesWhenMixed() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\", \"" + username + "|mailbox|4\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Then
        String getMessagesResponse = given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + username + "|mailbox|1\", \"" + username + "|mailbox|2\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .content(startsWith("[[\"messages\","))
                .extract()
                .asString();

        assertThat(jsonPath.parse(getMessagesResponse).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(getMessagesResponse).<Integer>read("$.[0].[1].list.length()")).isEqualTo(1);
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", presumedMessageId))
        .when()
            .post("/jmap")
        // Then
        .then()
            .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
            .log().ifValidationFails();
    }

    private ResponseSpecification getSetMessagesUpdateOKResponseAssertions(String messageId) {
        String responseHeaderPath = "[0][0]";
        String responseBodyBasePath = "[0][1]";
        ResponseSpecBuilder builder = new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectBody(responseHeaderPath, equalTo("messagesSet"))
                .expectBody(responseBodyBasePath +".updated", hasSize(1))
                .expectBody(responseBodyBasePath +".updated", contains(messageId))
                .expectBody(responseBodyBasePath +".error", isEmptyOrNullString())
                .expectBody(responseBodyBasePath +".notUpdated", not(hasKey(messageId)));
        return builder.build();
    }

    @Test
    public void setMessagesShouldMarkAsReadWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", presumedMessageId))
        // When
                .post("/jmap");
        // Then
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
        .when()
                .post("/jmap")
        .then()
                .statusCode(200)
                .body("[0][0]", equalTo("messages"))
                .body("[0][1].list", hasSize(1))
                .body("[0][1].list[0].isUnread", equalTo(false))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.SEEN));
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsUnreadWhenIsUnreadPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.SEEN));
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
                .post("/jmap");
        // Then
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
        .when()
                .post("/jmap")
        .then()
                .body("[0][0]", equalTo("messages"))
                .body("[0][1].list", hasSize(1))
                .body("[0][1].list[0].isUnread", equalTo(true))
                .log().ifValidationFails();
    }


    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsFlaggedPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsFlaggedWhenIsFlaggedPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
                .post("/jmap");
        // Then
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
        .when()
                .post("/jmap")
        .then()
                .body("[0][0]", equalTo("messages"))
                .body("[0][1].list", hasSize(1))
                .body("[0][1].list[0].isFlagged", equalTo(true))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldRejectUpdateWhenPropertyHasWrongType() throws MailboxException {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        String messageId = username + "|mailbox|1";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\" } } }, \"#0\"]]", messageId))
        .when()
                .post("/jmap")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("[0][0]", equalTo("messagesSet"))
                .body("[0][1].notUpdated", hasKey(messageId))
                .body("[0][1].notUpdated[\""+messageId+"\"].type", equalTo("invalidProperties"))
                .body("[0][1].notUpdated[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
                .body("[0][1].notUpdated[\""+messageId+"\"].description", startsWith("isUnread: Can not construct instance of java.lang.Boolean from String value '123': only \"true\" or \"false\" recognized\n" +
                        " at [Source: {\"isUnread\":\"123\"}; line: 1, column: 2] (through reference chain: org.apache.james.jmap.model.Builder[\"isUnread\"])"))
                .body("[0][1].updated", hasSize(0));
    }

    @Test
    @Ignore("Jackson json deserializer stops after first error found")
    public void setMessagesShouldRejectUpdateWhenPropertiesHaveWrongTypes() throws MailboxException {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        String messageId = username + "|mailbox|1";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\", \"isFlagged\" : null } } }, \"#0\"]]", messageId))
        .when()
                .post("/jmap")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("[0][0]", equalTo("messagesSet"))
                .body("[0][1].notUpdated", hasKey(messageId))
                .body("[0][1].notUpdated[\""+messageId+"\"].type", equalTo("invalidProperties"))
                .body("[0][1].notUpdated[\""+messageId+"\"].properties", hasSize(2))
                .body("[0][1].notUpdated[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
                .body("[0][1].notUpdated[\""+messageId+"\"].properties[1]", equalTo("isFlagged"))
                // .body("[0][1].notUpdated[\""+messageId+"\"].description", startsWith("Can not construct instance of java.lang.Boolean from String value '123': only \"true\" or \"false\" recognized"))
                .body("[0][1].updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldMarkMessageAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        // When
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", presumedMessageId))
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String presumedMessageId = username + "|mailbox|1";
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
                .post("/jmap");
        // Then
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
        .when()
                .post("/jmap")
        .then()
                .body("[0][0]", equalTo("messages"))
                .body("[0][1].list", hasSize(1))
                .body("[0][1].list[0].isAnswered", equalTo(true))
                .log().ifValidationFails();
    }

    @Test
    public void setMessageShouldReturnNotFoundWhenUpdateUnknownMessage() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        String nonExistingMessageId = username + "|mailbox|12345";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", nonExistingMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("[0][0]", equalTo("messagesSet"))
            .body("[0][1].notUpdated", hasKey(nonExistingMessageId))
            .body("[0][1].notUpdated[\""+nonExistingMessageId+"\"].type", equalTo("notFound"))
            .body("[0][1].notUpdated[\""+nonExistingMessageId+"\"].description", equalTo("message not found"))
            .body("[0][1].updated", hasSize(0));
    }
}