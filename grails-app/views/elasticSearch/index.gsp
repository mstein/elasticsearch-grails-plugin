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
    clear: both;
    display: block;
  }

  .box, .noticeBox, .right-menu {
    border: solid 1px #a9a9a9;
    border-radius: 5 5 5 5;
    -moz-border-radius: 5 5 5 5;
    -webkit-border-radius: 5 5 5 5;
    max-width: 80%;
    margin: auto;
    padding: 5px;
    margin-left:40px;
    margin-top: 10px;
    background-color: #FFFFFF;
    overflow: auto;
  }

  .noticeBox {
    border: solid 1px #7C9D00;
  }

  .right-menu {
    float:right;
    margin-left:2px;
    margin-top:0px;
  }

  .user-file {
    float: left;
    border: 1px solid #a9a9a9;
    width: 300px;
  }

  .user-file strong {
    color: #7C9D00;
  }

  .left {
    float: left;
    padding: 5px;
  }
  </style>
</head>
<body>
<div class="right-menu">
  <span class="title">Quick mass operations</span>
  <g:form action="createMassProducts">
    <label for="persisted">
      <input type="checkbox" name="persisted" id="persisted" value="true" ${params.persisted != null && params.boolean('persisted')? 'checked="checked"' : ''}/>
      Persisted in Database
    </label><br />
    <label for="batched">
      <input type="checkbox" name="batched" id="batched" value="true" ${params.batched != null && params.boolean('batched') ? 'checked="checked"' : ''}/>
      Batch operation
    </label><br />
    <label for="number">
      Number of product to create<br />
      <input type="text" name="number" id="number" value="${params.number ?: 1000}"/>
    </label><br />
    <input type="submit" value="Create Products"/>
  </g:form>
</div>
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
        <strong>Id:</strong>${u.id}<br/>
        <strong>Firstname:</strong>${u.firstname}<br/>
        <strong>Lastname:</strong>${u.lastname}<br/>
        <strong>Role:</strong>${u.role}<br/>
        <strong>Activity:</strong>${u.activity}
      </div>
    </g:each>
  </div>
</g:else>
<div class="box">
  <span class="title">Post a Tweet</span>
  <g:form controller="elasticSearch" action="postTweet">
    <p>
      <label for="activity-user">User :</label><br/>
      <g:select id="activity-user" name="user.id" from="${allUsers ?: []}" optionKey="id" optionValue="firstname" value="John"/>
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
        <input name="search" type="submit" value="Search"/>
        <input name="count" type="submit" value="Count hits only"/>
      </p>

    </g:form>
  </div>
</div>
<div class="box">
  <span class="title">Change user activity</span>
  <g:form controller="elasticSearch" action="updateActivity">
    <p>
      <label for="activity-user">User :</label><br/>
      <g:select id="activity-user" name="user.id" from="${allUsers ?: []}" optionKey="id" optionValue="firstname" value="John"/>
    </p>
    <p>
      <label for="message-search">New Activity</label>
      <br/>
      <input type="text" name="user.activity" id="user-activity" style="width:250px;"/>
    </p>
    <p>
      <input type="submit" value="Update"/>
    </p>
  </g:form>
</div>
<div class="box">
  <span class="title">Create event</span>
  <g:form controller="elasticSearch" action="createEvent">
    <p>
      <label for="event-name">Name :</label><br/>
      <input type="text" name="event.name" id="event-name" style="width:200px;"/>
    </p>
    <p>
      <label for="event-description">Description</label>
      <br/>
      <input type="text" name="event.description" id="event-description" style="width:350px;"/>
    </p>
    <p>
      <input type="submit" value="Create"/>
    </p>
  </g:form>
</div>
<g:if test="${Tweet.count() > 0}">
  <div class="box">
    <span class="title">Manage Existing tweets</span>
    <g:each var="tweet" in="${Tweet.all}">
      <div class="tweet">
        <strong>Tweet from ${tweet.user?.firstname} ${tweet.user?.lastname}</strong>
        (<g:link controller="${controllerName}" action="deleteTweet" id="${tweet.id}">Delete</g:link>)<br/>
        <strong>Tags:</strong>
        %{
          out << tweet.tags?.collect { t -> t.name }?.join(', ')
        }% <br/>
        ${tweet.message.encodeAsHTML().replace('\n', '<br/>')}
      </div>
    </g:each>
  </div>
</g:if>
</body>
</html>