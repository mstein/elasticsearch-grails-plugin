%{--
  Test GSP
--}%
<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title>ElasticSearch Test</title>
  <style type="text/css">
  body {
    background-color: #F2F2F2;
  }

  .title {
    color: #7C9D00;
    font-weight: bolder;
    font-variant: small-caps;
  }

  .box, .noticeBox {
    border: solid 1px #a9a9a9;
    border-radius: 5 5 5 5;
    -moz-border-radius: 5 5 5 5;
    -webkit-border-radius: 5 5 5 5;
    width: 80%;
    margin: auto;
    padding: 5px;
    margin-top: 10px;
    background-color: #FFFFFF;
  }

  .noticeBox {
    border: solid 1px #7C9D00;
  }
  </style>
</head>
<body>
<g:if test="${flash.notice}">
  <div class="noticeBox">
    ${flash.notice}
  </div>
</g:if>
<div class="box">
  <g:form controller="elasticSearch" action="createUsers">
    <p>
      <input type="submit" value="Create users"/>
    </p>
  </g:form>
</div>
<div class="box">
  <span class="title">Post a Tweet</span>
  <g:form controller="elasticSearch" action="postTweet">
    <p>
      <label for="user-firstname">Firstname</label><br/>
      <input type="text" name="user.firstname" id="user-firstname" value="John"/>
      <br/>
      <label for="user-lastname">Lastname</label><br/>
      <input type="text" name="user.lastname" id="user-lastname" value="DA"/>
    </p>
    <p>
      <label for="tweet">Tweet</label><br/>
      <textarea rows="7" cols="80" name="tweet.message" id="tweet"></textarea>
    </p>
    <p>
      <label for="tags">Tags (use comma to add multiple tags)</label><br/>
      <input type="text" name="tags" id="tags"/>
    </p>
    <p>
      <input type="submit" value="Send"/>
    </p>
  </g:form>
</div>
<div class="box">
  <span class="title">Search for tweets</span>
  <g:form controller="elasticSearch" action="searchForUserTweets">
    %{--<p>
      <label for="user-lastname-search">User lastname</label>
      <br />
      <input type="text" name="user.lastname.search" id="user-lastname-search" value="DA" disabled="disabled"/>
    </p>--}%
    <p>
      <label for="message-search">Query terms</label>
      <br />
      <input type="text" name="message.search" id="message-search" style="width:250px;"/>
    </p>
    <p>
      <input type="submit" value="Search"/>
    </p>
  </g:form>
</div>
<g:if test="${hits}">
  Search results
  <g:each in="${hits}" var="hit">
    ${hit.source.user} : ${hit.source.tweet}
  </g:each>
</g:if>
</body>
</html>