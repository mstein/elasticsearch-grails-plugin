%{--
  Test GSP
--}%
<%@ page import="test.Tweet; test.User" contentType="text/html;charset=UTF-8" %>
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
    clear:both;
    display:block;
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
    overflow:auto;
  }

  .noticeBox {
    border: solid 1px #7C9D00;
  }

  .user-file {
    float:left;
    border:1px solid #a9a9a9;
    width:300px;
  }

  .user-file strong {
    color:#7C9D00;
  }

  .left {
    float:left;
    padding: 5px;
  }
  </style>
</head>
<body>
<g:if test="${flash.notice}">
  <div class="noticeBox">
    ${flash.notice}
  </div>
</g:if>
<g:if test="${User.count() == 0}">
  <div class="box">
    <g:form controller="elasticSearch" action="createUsers">
      <p>
        <input type="submit" value="Create users"/>
      </p>
    </g:form>
  </div>
</g:if>
<g:else>
  <g:set var="allUsers" value="${User.all}"/>
  <div class="box">
    <span class="title">Available users</span>
    <g:each var="u" in="${User.all}">
      <div class="user-file">
        <strong>Id: </strong>${u.id}<br/>
        <strong>Firstname: </strong>${u.firstname}<br/>
        <strong>Lastname: </strong>${u.lastname}<br/>
        <strong>Role: </strong>${u.role}<br/>
        <strong>Activity: </strong>${u.activity}
      </div>
    </g:each>
  </div>
</g:else>
<div class="box">
  <span class="title">Post a Tweet</span>
  <g:form controller="elasticSearch" action="postTweet">
    <p>
      <label for="activity-user">User :</label><br/>
      <g:select id="activity-user" name="user.id" from="${allUsers ?: []}" optionKey="id" optionValue="firstname" value="John" />
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
  <span class="title">Search</span>
  <div class="left">
    <g:form controller="elasticSearch" action="searchForUserTweets">
      <p>
        <label for="message-search">Search for tweets</label>
        <br/>
        <input type="text" name="message.search" id="message-search" style="width:250px;"/>
        <input type="submit" value="Search"/>
      </p>
    </g:form>
  </div>
  <div class="left">
    <g:form controller="elasticSearch" action="searchAll">
      <p>
        <label for="search-query">Search for anything</label>
        <br/>
        <input type="text" name="query" id="search-query" style="width:250px;"/>
        <g:select id="search-user" name="firstname" from="${allUsers ?: []}" optionKey="firstname" optionValue="firstname" noSelection="['':'Any user']" value=""/>
        <input type="submit" value="Search"/>
      </p>

    </g:form>
  </div>
</div>
<div class="box">
  <span class="title">Change user activity</span>
  <g:form controller="elasticSearch" action="updateActivity">
    <p>
      <label for="activity-user">User :</label><br/>
      <g:select id="activity-user" name="user.id" from="${allUsers ?: []}" optionKey="id" optionValue="firstname" value="John" />
    </p>
    <p>
      <label for="message-search">New Activity</label>
      <br />
      <input type="text" name="user.activity" id="user-activity" style="width:250px;"/>
    </p>
    <p>
      <input type="submit" value="Update"/>
    </p>
  </g:form>
</div>
<g:if test="${Tweet.count() > 0}">
  <div class="box">
    <span class="title">Manage Existing tweets</span>
    <g:each var="tweet" in="${Tweet.all}">
      <div class="tweet">
        <strong>Tweet from ${tweet.user?.firstname} ${tweet.user?.lastname}</strong>
        (<g:link controller="${controllerName}" action="deleteTweet" id="${tweet.id}">Delete</g:link>)<br />
        <strong>Tags:</strong>
        %{
          out << tweet.tags?.collect{ t -> t.name }?.join(', ')
        }% <br />
        ${tweet.message.encodeAsHTML().replace('\n','<br/>')}
      </div>
    </g:each>
  </div>
</g:if>
</body>
</html>