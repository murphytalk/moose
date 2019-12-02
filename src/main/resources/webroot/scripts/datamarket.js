function build_header(table_selector,headers) {
    var head = $("<thead>").append();
    var tr = $("<tr>");
    $.each(headers, function (idx, h) {
        tr.append($("<th>").append(h));
    });
    head.append(tr);
    $(table_selector+" thead").replaceWith(head);
};

function capitalizeFLetter(string) {
    return string[0].toUpperCase() +  string.slice(1);
}

$(document).ready(function () {
    $.getJSON("/api/md/initpaint", function(data){
        if(data.length > 0){
            var items = window.localStorage.getItem('md_items_per_page');
            items =items == null ? 15 : parseInt(items);

            var header = ['Ticker'];
            var cols = [{ "data": "ticker" }];
            for (var key in data[0]){
                if(key.localeCompare("ticker") !=0 && ! key.includes("_time") ){
                    header.push(capitalizeFLetter(key));
                    cols.push({ "data": key });
                }
            }
            cols.push({ "data": "received_time" });
            cols.push({ "data": "sent_time" });
            header.push("Rcv Time");
            header.push("Sent Time");
            build_header('#mdtable', header);

            // install item per page menu callback
            $('#mdtable').on('length.dt',function(e,settings, len){
                window.localStorage.setItem('md_items_per_page', len.toString());
            });

            $('#mdtable').DataTable({
                data: data,
                pageLength: items,
                order: [[0, 'asc'], [1, 'asc']],
                lengthMenu: [10, 15, 50, 100],
                columns: cols,
                rowId: 'ticker'
            });

            //real time update
            var eb = new EventBus(window.location.protocol + "//" + window.location.host + "/eventbus");
            eb.onopen = function () {
                 eb.registerHandler("marketdata_status", function (error, message) {
                     if(error){
                         console.log(error);
                     }
                     else if(message && ("body" in message)) {
                         var t = $('#mdtable').DataTable();

                         var row = t.row('#' + message["body"]["ticker"])
                         if(row.data()){
                             row.data(message["body"]);
                         }
                         else{
                             t.row.add(message["body"]).draw( false );
                         }
                     }
                 })
            };
        }
        else{
            console.log("no market data");
        }
    });
});
