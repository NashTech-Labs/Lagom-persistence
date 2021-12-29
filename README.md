# Lagom-Persistence

In this project I'm simply demonstrate the Lagom persistence using a very simple example.

---
Example info :-

&emsp;&emsp;There is an Accountant service that takes care of all the bills/invoices provided by you<br>&emsp;&emsp;and generates a bill with a total amount that you have to pay.

To run this project in your system simply clone it and run the `sbt runAll` command through your terminal under the project location. After running the command in the end the output should look like:
```[info] play.api.Play [] - Application started (Dev) (no global state)
[info] Service AccountantService-impl listening for HTTP on 127.0.0.1:50409
[info] (Service started, press enter to stop and go back to the console...)
```

Now the service is running successfully and user can hit the endpoint. To check the service that it's working properly or not simply go to any browser and write `http://127.0.0.1:50409/api/hello/Yash` in the url tab and hit enter/search button.

The service will generates the response as string `Hello! Yash from Lagom service`

Now after getting above output you can say that the service is working properly

Now to add the invoices so that accountant service calculate the bill you have to hit the service with a POST call using URL - 
`http://127.0.0.1:50409/api/accountant/1234/addInvoices` and In body you have to give the invoice in JSON format.<br/>
For example - JSON will be:

```
{
    "invoice" :
     [
        {
           "id": "yashInvoice1",
           "amount": 1000
        },
        {
           "id": "yashInvoice2",
           "amount": 1000
        }
    ]
}
```
Here we are sending two invoices. Our service will first persist the event for the incoming command and then store the state in the DB to generate the final bill.

If you again hit the URL with the same body it will give you an error that the invoice already exists or something like that.

Now for other options you can explore the project on your own.

**Note**: SBT should be installed in your system, or you can use an IDE for example - In Intellij you have to open the cloned folder 
and wait until it loaded successfully, 
now open the terminal and run command `sbt runAll` it will run your project and generate the output at mentioned port.