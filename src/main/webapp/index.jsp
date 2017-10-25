<html>
<body>
<h2>OData Olingo V4 Demo Service</h2>
<a href="DemoServiceTest.svc/">OData Olingo V4 Demo Service</a>
<h3>Sample Links</h3>
<ul>
    <li>
        <h4>Read Entities</h4>
        <ul>
            <li>
                <a href="DemoServiceTest.svc/Profiles">View all profiles</a>
            </li>
            <li>
                <a href="DemoServiceTest.svc/Reviews">View Reviews</a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles(1)">View Profile 1</a>

            </li>
            <li>
                <a href="DemoServiceTest.svc/Profiles(1)/Reviews">View Reviews of Profile 1 (Navigation)</a>

            </li>
            <li>
                <a href="DemoServiceTest.svc/Profiles(1)/Reviews?$skip=1">View Reviews of Profile 1 by skipping
                    1($skip)</a>
            </li>
            <li>
                <a href="DemoServiceTest.svc/Profiles(1)/Reviews?$count=true">Count the number of Reviews of Profile
                    1($count)</a>
            </li>
            <li>
                <a href="DemoServiceTest.svc/Profiles(1)/Reviews?$top=2">View top 2 reviews of profile 1 ($top)</a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Reviews?$expand=Profile">Expand the profiles of all reviews ($expand) </a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Reviews(1)?$expand=Profile">Expand the profile of the person who has put
                    Review 1 ($expand) </a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles?$orderby=Name">Order all profiles by Name ($orderBy) </a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles?$select=Name">Select all the names ($select) </a>
            </li>


            <h3>Filters Examples </h3>
            <h5>(Try all the other filters as well)</h5>
            <li>
                <a href="DemoServiceTest.svc/Profiles?$filter=ID eq 1">ID eq 1 ($filter)</a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles?$filter=ID ne 1"> ID not equal 1 ($filter) </a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles?$filter=contains(Name,'Chathura')">$filter by name Chathura using
                    contains</a>
            </li>

            <li>
                <a href="DemoServiceTest.svc/Profiles?$filter=not contains(Name,'Chathura')">$filter by name using not
                    contains where name is not Chathura</a>
            </li>


            <li>
                <a href="DemoServiceTest.svc/Profiles?$filter=contains(Name,'Chathura') or ID eq 1">$filter by name
                    Chathura using contains or by ID</a>
            </li>


            <h3>Functions: Try following in Postman(Not supported in browser)</h3>
            <h5>http://10.17.62.13:8080/DemoServiceTest.svc/CountProfiles(Value=1)</h5>
            <p>Note:This counts the number of profiles that has received 1 review and returns it</p>

            <h3>Actions : Retrieve a given number of entities or Reset list(Not supported in browser)</h3>
            <p>Content-Type : application/json</p>
            <p>Method : POST</p>
            <h5>http://localhost:8080/DemoServiceTest.svc/Reset <br>Content:{ "Value":2 }</h5>
            <p>Note:This resets the list to only 2 reviews </p>

            Then try GET : <h5> http://10.17.62.13:8080/DemoServiceTest.svc/Reviews </h5>


            <p>Method : POST </p>
            <h5> http://localhost:8080/DemoServiceTest.svc/Reset </h5>Content:{}
            </p>
            <p>Note:This resets the list back to all lists</p>
        </ul>
    </li>

</ul>
</body>
</html>
